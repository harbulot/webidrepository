/*-----------------------------------------------------------------------
  
Copyright (c) 2010, The University of Manchester, United Kingdom.
All rights reserved.

Redistribution and use in source and binary forms, with or without 
modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, 
      this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright 
      notice, this list of conditions and the following disclaimer in the 
      documentation and/or other materials provided with the distribution.
 * Neither the name of the The University of Manchester nor the names of 
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

  Author........: Bruno Harbulot

-----------------------------------------------------------------------*/
package uk.ac.manchester.rcs.bruno.webidrepository;

import java.io.IOException;
import java.io.OutputStream;

import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.rdfxml.RDFXMLWriter;
import org.restlet.data.MediaType;
import org.restlet.representation.OutputRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

/**
 * @author Bruno Harbulot (Bruno.Harbulot@manchester.ac.uk)
 * 
 */
public class SesameContextDocumentResource extends ServerResource {
    protected URI context;
    protected volatile RepositoryConnection repositoryConnection;

    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        try {
            Repository repository = (Repository) getContext().getAttributes()
                    .get(WebidModule.SESAME_REPOSITORY_CTXATTR_NAME);

            if (this.context == null) {
                URI context = repository.getValueFactory().createURI(
                        getRequest().getResourceRef().toString());
                this.context = context;
            }

            this.repositoryConnection = repository.getConnection();

            RepositoryResult<Statement> result = repositoryConnection
                    .getStatements(null, null, null, true, context);
            setExisting(result.hasNext());
        } catch (RepositoryException e) {
            throw new ResourceException(e);
        }
    }

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

    @Get("xml")
    public Representation toXml() {
        final RepositoryConnection repositoryConnection = this.repositoryConnection;
        Representation rep = new OutputRepresentation(
                MediaType.APPLICATION_RDF_XML) {
            @Override
            public void write(OutputStream outputStream) throws IOException {
                try {
                    RDFXMLWriter rdfXmlWriter = new RDFXMLWriter(outputStream);
                    repositoryConnection.export(rdfXmlWriter, context);
                } catch (RepositoryException e) {
                    throw new IOException(e);
                } catch (RDFHandlerException e) {
                    throw new IOException(e);
                } finally {
                    try {
                        repositoryConnection.close();
                    } catch (RepositoryException e) {
                        throw new IOException(e);
                    }
                }
            }
        };
        this.repositoryConnection = null;
        return rep;
    }
}
