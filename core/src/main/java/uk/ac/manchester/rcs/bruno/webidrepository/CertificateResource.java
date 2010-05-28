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
import java.io.StringReader;
import java.io.StringWriter;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.restlet.data.MediaType;
import org.restlet.representation.OutputRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

/**
 * This is a resource class for the records.
 * 
 * @author Bruno Harbulot (Bruno.Harbulot@manchester.ac.uk)
 * 
 * @param <RecordT>
 *            type of record
 * @param <CoreDataT>
 *            type of core-data (XmlBeans)
 */
public class CertificateResource extends ServerResource {
    private static final Log LOGGER = LogFactory
            .getLog(CertificateResource.class);

    private X509Certificate certificate;

    protected volatile RepositoryConnection repositoryConnection;

    @Override
    public Representation handle() throws ResourceException {
        try {
            return super.handle();
        } finally {
            try {
                RepositoryConnection repositoryConnection = this.repositoryConnection;
                if (repositoryConnection != null) {
                    repositoryConnection.close();
                }
            } catch (RepositoryException e) {
                throw new ResourceException(e);
            }
        }
    }

    @Override
    public void doInit() {
        super.doInit();
        try {
            Repository repository = (Repository) getContext().getAttributes()
                    .get(WebidModule.SESAME_REPOSITORY_CTXATTR_NAME);

            String parentUri = getRequest().getResourceRef().getParentRef()
                    .toString();
            URI context = repository.getValueFactory().createURI(parentUri);

            this.repositoryConnection = repository.getConnection();

            final RepositoryConnection repositoryConnection = this.repositoryConnection;

            StringBuilder queryStringBuilder = new StringBuilder();
            queryStringBuilder.append("SELECT * ");
            queryStringBuilder.append(String.format("FROM CONTEXT <%s> ",
                    context));
            queryStringBuilder.append(String.format(
                    " {<%s#me>} rdf:type {foaf:Person}, ", context));
            queryStringBuilder.append(String.format(
                    " [{<%s#me>} man:x509PemCert {x509Cert}] ", context));
            queryStringBuilder.append(String.format(
                    "USING NAMESPACE foaf = <%s>, man = <%s>",
                    WebidModule.FOAF_NS, WebidModule.FOAFSSLMANCHESTER_NS));
            try {
                TupleQuery tupleQuery = repositoryConnection.prepareTupleQuery(
                        QueryLanguage.SERQL, queryStringBuilder.toString(),
                        context.toString());
                TupleQueryResult result = tupleQuery.evaluate();
                if (result.hasNext()) {
                    BindingSet bindingSet = result.next();
                    Value x509Cert = bindingSet.getValue("x509Cert");

                    X509Certificate certificate = null;

                    if (x509Cert != null) {
                        PEMReader pemReader = new PEMReader(new StringReader(
                                x509Cert.stringValue()));
                        Object pemObject = pemReader.readObject();
                        if (pemObject instanceof X509Certificate) {
                            certificate = (X509Certificate) pemObject;
                        } else {
                            LOGGER
                                    .warn(String
                                            .format(
                                                    "What was meant to be a PEM certificate could not be read by the PEMReader (found and instance of %s instead)",
                                                    pemObject.getClass()
                                                            .getName()));
                        }
                        pemReader.close();
                    }
                    if (certificate == null) {
                        setExisting(false);
                    } else {
                        this.certificate = certificate;
                    }
                } else {
                    setExisting(false);
                }
            } catch (IOException e) {
                throw new ResourceException(e);
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

        setNegotiated(true);
    }

    @Get("crt|pem")
    public Representation toPem() {
        if (this.certificate != null) {
            try {
                final StringWriter sw = new StringWriter();
                PEMWriter pemWriter = new PEMWriter(sw);
                pemWriter.writeObject(this.certificate);
                pemWriter.close();
                String pemCert = sw.toString();
                return new StringRepresentation(pemCert, MediaType
                        .valueOf("application/x-pem-file"));
            } catch (IOException e) {
                throw new ResourceException(e);
            }
        } else {
            return null;
        }
    }

    @Get("cer")
    public Representation toDer() {
        if (this.certificate != null) {
            try {
                final byte[] encodedCert = this.certificate.getEncoded();
                return new OutputRepresentation(MediaType
                        .valueOf("application/x-x509-user-cert"),
                        encodedCert.length) {

                    @Override
                    public void write(OutputStream out) throws IOException {
                        out.write(encodedCert);
                    }
                };
            } catch (CertificateEncodingException e) {
                throw new ResourceException(e);
            }
        } else {
            return null;
        }
    }
}
