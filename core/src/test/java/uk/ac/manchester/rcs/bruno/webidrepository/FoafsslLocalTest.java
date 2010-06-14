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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;

import org.apache.derby.jdbc.EmbeddedDataSource;
import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;
import org.ini4j.Profile.Section;
import org.junit.Ignore;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Server;
import org.restlet.data.Protocol;

import uk.ac.manchester.rcs.corypha.core.CoryphaRootApplication;
import uk.ac.manchester.rcs.corypha.core.CoryphaTemplateUtil;

import freemarker.template.Configuration;
import freemarker.template.TemplateModelException;

/**
 * @author Bruno Harbulot
 * 
 */
@Ignore
public class FoafsslLocalTest {

    private static void loadConfig(Context appContext, InputStream configIs)
            throws InvalidFileFormatException, IOException,
            TemplateModelException {
        Ini ini = new Ini(configIs);

        Configuration freemarkerConfig = CoryphaTemplateUtil
                .getConfiguration(appContext);
        freemarkerConfig.setSharedVariable("maintitle", ini.get("core",
                "maintitle"));

        Section iniSection = ini.get("sidenav");
        CopyOnWriteArrayList<String> menuItemsHtml = new CopyOnWriteArrayList<String>();
        for (String iniMenuItem : iniSection.getAll("item")) {
            menuItemsHtml.add(iniMenuItem);
        }
        freemarkerConfig.setSharedVariable("sidemenuitems", menuItemsHtml);
        freemarkerConfig.setSharedVariable("sidemenutitle", iniSection
                .get("title"));
    }

    public static void main(String[] args) throws Throwable {
        Component component = new Component();
        Server server = component.getServers().add(Protocol.HTTPS, 8183);
        component.getClients().add(Protocol.CLAP);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        InputStream is = new FileInputStream(
                "/home/bruno/eclipse-3.5/workspace/jsslutils/trunk/certificates/src/main/resources/org/jsslutils/certificates/local/localhost.p12");
        keyStore.load(is, "testtest".toCharArray());
        is.close();

        System.setProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                org.eclipse.jetty.jndi.InitialContextFactory.class.getName());
        javax.naming.Context ctx = null;
        try {
            ctx = (javax.naming.Context) new InitialContext()
                    .lookup("java:comp");
            try {
                ctx = (javax.naming.Context) ctx.lookup("env");
            } catch (NameNotFoundException e) {
                ctx = ctx.createSubcontext("env");
            }
            javax.naming.Context envCtx = ctx;
            try {
                ctx = (javax.naming.Context) envCtx.lookup("webiddirectory");
            } catch (NameNotFoundException e) {
                ctx = envCtx.createSubcontext("webiddirectory");
            }
            ctx.rebind("signingKeyStore", keyStore);

            try {
                ctx = (javax.naming.Context) envCtx.lookup("jdbc");
            } catch (NameNotFoundException e) {
                ctx = envCtx.createSubcontext("jdbc");
            }
            EmbeddedDataSource ds = new EmbeddedDataSource();
            ds.setDatabaseName("target/testdb");
            ds.setCreateDatabase("true");
            ctx.rebind("webiddirectoryDS", ds);
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }

        server
                .getContext()
                .getParameters()
                .add(
                        "keystorePath",
                        "/home/bruno/eclipse-3.5/workspace/jsslutils/trunk/certificates/src/main/resources/org/jsslutils/certificates/local/localhost.p12");
        server.getContext().getParameters().add("keystoreType", "PKCS12");
        server.getContext().getParameters().add("keystorePassword", "testtest");

        CoryphaRootApplication cmsRootApplication = new CoryphaRootApplication();
        cmsRootApplication.setContext(component.getContext()
                .createChildContext());
        Context cmsRootAppContext = cmsRootApplication.getContext();

        cmsRootAppContext.getParameters().add(
                CoryphaRootApplication.MODULE_CLASSES_CTX_PARAM,
                "uk.ac.manchester.rcs.bruno.webidrepository.WebidModule");
        cmsRootAppContext.getParameters().add(
                "webiddirectory/signingKeyPassword", "testtest");
        cmsRootAppContext.getParameters().add("webiddirectory/issuerName",
                "CN=test");

        InputStream configIs = ClassLoader
                .getSystemResourceAsStream("config.ini");
        try {
            loadConfig(cmsRootAppContext, configIs);
        } finally {
            if (configIs != null) {
                configIs.close();
            }
        }

        component.getDefaultHost().attachDefault(cmsRootApplication);

        component.start();
    }
}
