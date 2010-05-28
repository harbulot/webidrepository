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

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.security.Authenticator;
import org.restlet.security.User;

/**
 * Authenticator based on the SSL client certificate. If a client certificate is
 * presented, it adds the Principal of its subject to the list of principals in
 * the request's ClientInfo. It also sets the user to be a new User based on
 * this Principal.
 * 
 * {@link #getPrincipal(List)} and {@link #getUser(Principal)} can be overridden
 * to change the default behaviour.
 * 
 * @author Bruno Harbulot (Bruno.Harbulot@manchester.ac.uk)
 */
public class ClientCertificateAuthenticator extends Authenticator {
    public ClientCertificateAuthenticator(Context context) {
        super(context);
    }

    /**
     * Extracts the Principal of the subject to use from a chain of certificate.
     * By default, this is the X500Principal of the subject subject of the first
     * certificate in the chain.
     * 
     * @see X509Certificate
     * @see X500Principal
     * @param certificateChain
     *            chain of client certificates.
     * @return Principal of the client certificate or null if the chain is
     *         empty.
     */
    protected List<Principal> getPrincipals(
            List<X509Certificate> certificateChain) {
        if ((certificateChain != null) && (certificateChain.size() > 0)) {
            ArrayList<Principal> principals = new ArrayList<Principal>();
            X509Certificate userCert = certificateChain.get(0);
            principals.add(userCert.getSubjectX500Principal());
            return principals;
        } else {
            return null;
        }
    }

    /**
     * Creates a new User based on the subject's X500Principal. By default, the
     * user name is the subject distinguished name, formatted accorded to RFC
     * 2253. Some may choose to extract the Common Name only, for example.
     * 
     * @param principal
     *            subject's Principal (most likely X500Principal).
     * @return User instance corresponding to this principal or null.
     */
    protected User getUser(Principal principal) {
        if (principal != null) {
            return new User(principal.getName());
        } else {
            return null;
        }
    }

    /**
     * Authenticates the call using the X.509 client certificate. The
     * verification of the credentials is normally done by the SSL layer, via
     * the TrustManagers.
     * 
     * It uses the certificate chain in the request's
     * "org.restlet.https.clientCertificates" attribute, adds the principal
     * returned from this chain by {@link #getPrincipal(List)} to the request's
     * ClientInfo and set the user to the result of {@link #getUser(Principal)}
     * if that user is non-null.
     */
    @Override
    protected boolean authenticate(Request request, Response response) {
        @SuppressWarnings("unchecked")
        List<X509Certificate> certchain = (List<X509Certificate>) request
                .getAttributes().get("org.restlet.https.clientCertificates");

        List<Principal> principals = getPrincipals(certchain);

        if ((principals != null) && (principals.size() > 0)) {
            request.getClientInfo().getPrincipals().addAll(principals);
            User user = getUser(principals.get(0));
            if (user != null) {
                request.getClientInfo().setUser(user);
            }
            return true;
        } else {
            return false;
        }
    }
}
