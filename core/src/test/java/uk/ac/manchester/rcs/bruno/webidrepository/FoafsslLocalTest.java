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
import java.io.InputStream;
import java.security.KeyStore;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;

import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;
import org.ini4j.Profile.Section;
import org.junit.Ignore;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Server;
import org.restlet.data.Protocol;

import uk.ac.manchester.rcs.bruno.webidrepository.WebidModule;
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

        KeyStore keyStore = KeyStore.getInstance("KeychainStore");
        keyStore.load(null, "-".toCharArray());

        System.setProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                "org.mortbay.naming.InitialContextFactory");
        javax.naming.Context ctx = null;
        try {
            ctx = (javax.naming.Context) new InitialContext()
                    .lookup("java:comp");
            try {
                ctx = (javax.naming.Context) ctx.lookup("env");
            } catch (NameNotFoundException e) {
                ctx = ctx.createSubcontext("env");
            }
            try {
                ctx = (javax.naming.Context) ctx.lookup("foafdirectory");
            } catch (NameNotFoundException e) {
                ctx = ctx.createSubcontext("foafdirectory");
            }
            ctx.rebind("signingKeyStore", keyStore);
            ctx.rebind("signingKeyPasswordArray", "-".toCharArray());
            ctx.rebind("issuerName", "CN=test");
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }

        server.getContext().getParameters().add("keystorePath", "NONE");
        server.getContext().getParameters()
                .add("keystoreType", "KeychainStore");
        server.getContext().getParameters().add("keystorePassword", "-");

        CoryphaRootApplication cmsRootApplication = new CoryphaRootApplication();
        cmsRootApplication.setContext(component.getContext()
                .createChildContext());
        Context cmsRootAppContext = cmsRootApplication.getContext();

        cmsRootAppContext.getParameters().add(
                CoryphaRootApplication.MODULE_CLASSES_CTX_PARAM,
                "uk.ac.manchester.rcs.bruno.webidrepository.WebidModule");
        cmsRootAppContext.getParameters().add(
                WebidModule.SESAME_MEMORYSTORE_DIR_CTXPARAM_NAME,
                "/tmp/sesametmp.1/");

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
