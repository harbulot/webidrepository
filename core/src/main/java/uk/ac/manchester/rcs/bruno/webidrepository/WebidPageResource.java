/*-----------------------------------------------------------------------
  
Copyright (c) 2007-2010, The University of Manchester, United Kingdom.
All rights reserved.

Redistribution and use in source and binary forms, with or without 
modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, 
      this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright 
      notice, this list of conditions and the following disclaimer in the 
      documentation and/or other materials provided with the distribution.
 * Neither the name of The University of Manchester nor the names of 
      its contributors may be used to endorse or promote products derived 
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
POSSIBILITY OF SUCH DAMAGE.

-----------------------------------------------------------------------*/
package uk.ac.manchester.rcs.bruno.webidrepository;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.openssl.PEMWriter;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.openrdf.model.BNode;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.rdfxml.RDFXMLWriter;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.OutputRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.representation.Variant;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import uk.ac.manchester.rcs.bruno.keygenapp.base.MiniCaCertGen;
import uk.ac.manchester.rcs.bruno.keygenapp.base.MiniCaConfiguration;
import uk.ac.manchester.rcs.corypha.core.CoryphaTemplateUtil;
import uk.ac.manchester.rcs.corypha.core.HibernateFilter;

/**
 * This is a resource class for the records.
 * 
 * @author Bruno Harbulot (Bruno.Harbulot@manchester.ac.uk)
 * 
 */
public class WebidPageResource extends SesameContextDocumentResource {
    private static final Log LOGGER = LogFactory
            .getLog(WebidPageResource.class);

    private Map<String, String> foafsslData;
    private boolean canModify = false;

    @Override
    public void doInit() {
        super.doInit();

        setNegotiated(true);
    }

    @Get("html")
    public Representation toHtml() {
        String webId = context + "#me";

        final RepositoryConnection repositoryConnection = this.repositoryConnection;

        StringBuilder queryStringBuilder = new StringBuilder();
        queryStringBuilder.append(String.format("PREFIX rdf: <%s> ",
                RDF.NAMESPACE));
        queryStringBuilder.append(String.format("PREFIX foaf: <%s> ",
                WebidModule.FOAF_NS));
        queryStringBuilder.append(String.format("PREFIX man: <%s> ",
                WebidModule.FOAFSSLMANCHESTER_NS));
        queryStringBuilder.append("SELECT * ");
        queryStringBuilder.append(String.format("FROM <%s> ", context));
        queryStringBuilder.append("WHERE { ");
        queryStringBuilder.append(String.format(" <%s#me> rdf:type ?t . ",
                context));
        queryStringBuilder.append(String.format(
                " OPTIONAL { <%s#me> foaf:givenName ?givenName } . ", context));
        queryStringBuilder.append(String
                .format(" OPTIONAL { <%s#me> foaf:familyName ?familyName } . ",
                        context));
        queryStringBuilder.append(String.format(
                " OPTIONAL { <%s#me> man:x509PemCert ?x509Cert } . ", context));
        queryStringBuilder.append("} ");
        try {
            try {
                TupleQuery tupleQuery = repositoryConnection.prepareTupleQuery(
                        QueryLanguage.SPARQL, queryStringBuilder.toString(),
                        context.toString());
                TupleQueryResult result = tupleQuery.evaluate();
                if (result.hasNext()) {
                    BindingSet bindingSet = result.next();
                    Value givenName = bindingSet.getValue("givenName");
                    Value familyName = bindingSet.getValue("familyName");
                    Value x509Cert = bindingSet.getValue("x509Cert");

                    this.foafsslData = new HashMap<String, String>();
                    LOGGER.info(String.format("%s: %s %s", webId, givenName,
                            familyName));
                    this.foafsslData.put("webid", webId);
                    if (familyName != null) {
                        this.foafsslData.put("familyName", familyName
                                .stringValue());
                    }
                    if (givenName != null) {
                        this.foafsslData.put("givenName", givenName
                                .stringValue());
                    }
                    if (x509Cert != null) {
                        this.foafsslData
                                .put("x509Cert", x509Cert.stringValue());
                    }
                } else {
                    LOGGER.info("No result");
                    setExisting(false);
                }
            } finally {
                repositoryConnection.close();
            }
        } catch (RepositoryException e) {
            throw new ResourceException(e);
        } catch (MalformedQueryException e) {
            throw new ResourceException(e);
        } catch (QueryEvaluationException e) {
            throw new ResourceException(e);
        } finally {
            this.repositoryConnection = null;
        }

        HashMap<String, Object> data = new HashMap<String, Object>();
        if (this.foafsslData != null) {
            data.put("foaf", this.foafsslData);
        }
        data.put("canModify", this.canModify);
        return new TemplateRepresentation("foafprofile.ftl.html",
                CoryphaTemplateUtil.getConfiguration(getContext()), data,
                MediaType.TEXT_HTML);
    }

    @Post
    public Representation accept(Representation entity)
            throws ResourceException {
        final RepositoryConnection repositoryConnection = this.repositoryConnection;

        StringBuilder queryStringBuilder = new StringBuilder();
        queryStringBuilder.append("SELECT * ");
        queryStringBuilder.append(String.format("FROM CONTEXT <%s> ", context));
        queryStringBuilder.append(String.format(
                " {<%s#me>} rdf:type {foaf:Person}, ", context));
        queryStringBuilder.append(String.format(
                " [{<%s#me>} man:x509PemCert {x509Cert}] ", context));
        queryStringBuilder.append(String.format(
                "USING NAMESPACE foaf = <%s>, man = <%s>", WebidModule.FOAF_NS,
                WebidModule.FOAFSSLMANCHESTER_NS));
        try {
            try {
                TupleQuery tupleQuery = repositoryConnection.prepareTupleQuery(
                        QueryLanguage.SERQL, queryStringBuilder.toString(),
                        context.toString());
                TupleQueryResult result = tupleQuery.evaluate();
                if (result.hasNext()) {
                    BindingSet bindingSet = result.next();
                    Value x509Cert = bindingSet.getValue("x509Cert");

                    HashMap<String, String> dataModel = new HashMap<String, String>();
                    if (x509Cert != null) {
                        dataModel.put("x509Cert", x509Cert.stringValue());
                    }

                    if (entity.isCompatible(new Variant(
                            MediaType.APPLICATION_WWW_FORM))) {

                        Form form = new Form(entity);

                        MiniCaConfiguration configuration = (MiniCaConfiguration) getContext()
                                .getAttributes()
                                .get(
                                        WebidModule.MINICA_CONFIGURATION_CTXATTR_NAME);

                        String webId = context.toString() + "#me";
                        String spkacData = form.getFirstValue("spkac");
                        String pemCsrData = form.getFirstValue("csrdata");
                        String cn = form.getFirstValue("cn");

                        Date startDate = new Date();
                        Date endDate = new Date(startDate.getTime() + 365L
                                * 24L * 60L * 60L * 1000L);
                        X509Name subjectDn;
                        if ((cn == null) || cn.isEmpty()) {
                            subjectDn = new X509Name(new DERSequence());
                        } else {
                            subjectDn = new X509Name("CN=" + cn);
                        }

                        Representation returnRep;

                        X509Certificate cert;
                        if ((spkacData == null) || spkacData.isEmpty()) {
                            cert = MiniCaCertGen.createCertFromPemCsr(
                                    configuration.getCaPublicKey(),
                                    configuration.getCaPrivKey(), pemCsrData,
                                    subjectDn, configuration.getIssuerName(),
                                    startDate, endDate, webId, configuration
                                            .nextCertificateSerialNumber());
                        } else {
                            cert = MiniCaCertGen.createCertFromSpkac(
                                    configuration.getCaPublicKey(),
                                    configuration.getCaPrivKey(), spkacData,
                                    subjectDn, configuration.getIssuerName(),
                                    startDate, endDate, webId, configuration
                                            .nextCertificateSerialNumber());
                        }

                        StringWriter sw = new StringWriter();
                        PEMWriter pemWriter = new PEMWriter(sw);
                        pemWriter.writeObject(cert);
                        pemWriter.close();
                        String pemCert = sw.toString();

                        if ((spkacData == null) || spkacData.isEmpty()) {
                            returnRep = new StringRepresentation(pemCert,
                                    MediaType.valueOf("application/x-pem-file"));
                        } else {
                            final byte[] encodedCert = cert.getEncoded();
                            returnRep = new OutputRepresentation(MediaType
                                    .valueOf("application/x-x509-user-cert"),
                                    encodedCert.length) {

                                @Override
                                public void write(OutputStream out)
                                        throws IOException {
                                    out.write(encodedCert);
                                }
                            };
                        }
                        ValueFactory vf = repositoryConnection
                                .getValueFactory();

                        Resource webid = vf
                                .createURI(context.toString(), "#me");

                        URI predicate = vf
                                .createURI(WebidModule.FOAFSSLMANCHESTER_NS,
                                        "x509PemCert");
                        Value value = vf.createLiteral(pemCert);
                        repositoryConnection.add(webid, predicate, value,
                                context);

                        PublicKey publicKey = cert.getPublicKey();
                        if (publicKey instanceof RSAPublicKey) {
                            RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;

                            BNode keyBnode = vf.createBNode();
                            predicate = RDF.TYPE;
                            value = vf.createURI(WebidModule.RSA_NS,
                                    "RSAPublicKey");
                            repositoryConnection.add(keyBnode, predicate,
                                    value, context);

                            predicate = vf.createURI(WebidModule.CERT_NS,
                                    "identity");
                            repositoryConnection.add(keyBnode, predicate,
                                    webid, context);

                            BNode modulusBnode = vf.createBNode();
                            predicate = vf
                                    .createURI(WebidModule.CERT_NS, "hex");
                            value = vf.createLiteral(rsaPublicKey.getModulus()
                                    .toString(16));
                            repositoryConnection.add(modulusBnode, predicate,
                                    value, context);

                            predicate = vf.createURI(WebidModule.RSA_NS,
                                    "modulus");
                            repositoryConnection.add(keyBnode, predicate,
                                    modulusBnode, context);

                            BNode exponentBnode = vf.createBNode();
                            predicate = vf.createURI(WebidModule.CERT_NS,
                                    "decimal");
                            value = vf.createLiteral(rsaPublicKey
                                    .getPublicExponent().toString(10));
                            repositoryConnection.add(exponentBnode, predicate,
                                    value, context);

                            predicate = vf.createURI(WebidModule.RSA_NS,
                                    "public_exponent");
                            repositoryConnection.add(keyBnode, predicate,
                                    exponentBnode, context);
                        }

                        repositoryConnection.commit();

                        try {
                            sw = new StringWriter();
                            RDFXMLWriter rdfXmlWriter = new RDFXMLWriter(sw);
                            repositoryConnection.export(rdfXmlWriter, context);
                            RdfDocumentContainer rdfDocContainer = this.rdfDocContainer;
                            if ((rdfDocContainer.getId() == null)
                                    || (!rdfDocContainer.getId().equals(context
                                            .toString()))) {
                                rdfDocContainer = new RdfDocumentContainer();
                                rdfDocContainer.setId(context.toString());
                                LOGGER
                                        .warn("Mismatch between RdfDocumentContainer IDs, this should not happen!");
                            }
                            rdfDocContainer.setRdfContent(sw.toString());
                            Session session = HibernateFilter.getSession(
                                    getContext(), getRequest());
                            session.saveOrUpdate(rdfDocContainer);
                            session.getTransaction().commit();
                        } catch (RDFHandlerException e) {
                            throw new ResourceException(e);
                        } catch (HibernateException e) {
                            throw new ResourceException(e);
                        }

                        setExisting(true);
                        return returnRep;
                    } else {
                        setExisting(true);
                        return get();
                    }
                } else {
                    setExisting(false);
                    return null;
                }
            } finally {
                this.repositoryConnection = null;
                repositoryConnection.close();
            }
        } catch (RepositoryException e) {
            throw new ResourceException(e);
        } catch (MalformedQueryException e) {
            throw new ResourceException(e);
        } catch (QueryEvaluationException e) {
            throw new ResourceException(e);
        } catch (InvalidKeyException e) {
            throw new ResourceException(e);
        } catch (IllegalStateException e) {
            throw new ResourceException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new ResourceException(e);
        } catch (SignatureException e) {
            throw new ResourceException(e);
        } catch (CertificateException e) {
            throw new ResourceException(e);
        } catch (NoSuchProviderException e) {
            throw new ResourceException(e);
        } catch (IOException e) {
            throw new ResourceException(e);
        } finally {
            this.repositoryConnection = null;
        }
    }
}
