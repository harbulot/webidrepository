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

import java.security.Security;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hibernate.cfg.AnnotationConfiguration;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.resource.Directory;
import org.restlet.routing.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.manchester.rcs.bruno.keygenapp.base.MiniCaConfiguration;
import uk.ac.manchester.rcs.bruno.keygenapp.base.MiniCaConfiguration.ConfigurationException;
import uk.ac.manchester.rcs.corypha.core.CoryphaApplication;
import uk.ac.manchester.rcs.corypha.core.CoryphaModule;
import uk.ac.manchester.rcs.corypha.core.CoryphaTemplateUtil;
import uk.ac.manchester.rcs.corypha.core.HibernateFilter;
import uk.ac.manchester.rcs.corypha.core.IApplicationProvider;
import uk.ac.manchester.rcs.corypha.core.IHibernateConfigurationContributor;
import uk.ac.manchester.rcs.corypha.core.IMenuProvider;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;

/**
 * @author Bruno Harbulot
 * 
 */
public class WebidModule extends CoryphaModule implements IApplicationProvider,
        IMenuProvider, IHibernateConfigurationContributor {
    @SuppressWarnings("unused")
    private final static Logger LOGGER = LoggerFactory
            .getLogger(WebidModule.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static final String ITEMS_PATHELEMENT = "items";
    public static final String MAIN_ID_ATTRIBUTE = "uk.ac.nanocmos.datamanagement.service.attr.main_id";
    public static final String MINICA_CONFIGURATION_CTXATTR_NAME = "uk.ac.manchester.rcs.foafssl.minicaconfig";

    public static final String FOAFDIRECTORY_SESAME_REPOSITORY_ATTRIBUTE = "uk.ac.manchester.rcs.foafssl.sesame_repository";

    public final static String FOAF_NS = "http://xmlns.com/foaf/0.1/";
    public final static String FOAFSSLMANCHESTER_NS = "http://www.rcs.manchester.ac.uk/research/FoafSslShib/#";
    public final static String CERT_NS = "http://www.w3.org/ns/auth/cert#";
    public final static String RSA_NS = "http://www.w3.org/ns/auth/rsa#";

    public static class Application1 extends CoryphaApplication {
        @Override
        public String getAutoPrefix() {
            return "webid/";
        }

        @Override
        public Restlet createInboundRoot() {
            try {
                getMetadataService().addExtension("pem",
                        MediaType.valueOf("application/x-pem-file"));
                getMetadataService().addExtension("crt",
                        MediaType.valueOf("application/x-pem-file"));
                getMetadataService().addExtension("cer",
                        MediaType.valueOf("application/x-x509-user-cert"));

                getTunnelService().setEnabled(true);
                getTunnelService().setExtensionsTunnel(true);

                MiniCaConfiguration miniCaConfiguration = new MiniCaConfiguration();
                miniCaConfiguration.init();

                getContext().getAttributes().put(
                        MINICA_CONFIGURATION_CTXATTR_NAME, miniCaConfiguration);

                Repository repository = new SailRepository(new MemoryStore());
                repository.initialize();
                getContext().getAttributes().put(
                        FOAFDIRECTORY_SESAME_REPOSITORY_ATTRIBUTE, repository);

                Configuration cfg = CoryphaTemplateUtil
                        .getConfiguration(getContext());
                CoryphaTemplateUtil
                        .addTemplateLoader(cfg, new ClassTemplateLoader(
                                WebidModule.class, "templates"));

                Router router = new Router(getContext());
                router.setDefaultMatchingMode(Router.MODE_FIRST_MATCH);

                router.attach(String.format("profile/{%s}/certificate",
                        MAIN_ID_ATTRIBUTE), CertificateResource.class);
                router.attach(
                        String.format("profile/{%s}/", MAIN_ID_ATTRIBUTE),
                        WebidPageResource.class);
                router.attachDefault(WebidCreationPageResource.class);
                router.setDefaultMatchingQuery(false);

                HibernateFilter hibernateFilter = new HibernateFilter(
                        getContext(), router);

                return hibernateFilter;
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            } catch (RepositoryException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public CoryphaApplication getApplication() {
            return this;
        }

        @Override
        public Restlet getHtdocsRestlet() {
            Directory htdocsDirectory = new Directory(getContext(),
                    "clap://thread/uk/ac/manchester/rcs/bruno/webidrepository/htdocs");
            return htdocsDirectory;
        }
    }

    private final Application1 application1 = new Application1();
    private final List<MenuItem> menuItems = Collections
            .unmodifiableList(Arrays.asList(new MenuItem[] { new MenuItem(
                    "WebID", getApplication().getAutoPrefix()) }));

    @Override
    public CoryphaApplication getApplication() {
        return application1;
    }

    @Override
    public List<MenuItem> getMenuItems() {
        return menuItems;
    }

    @Override
    public void configureHibernate(AnnotationConfiguration configuration) {
        configuration.addPackage("uk.ac.manchester.rcs.bruno.webidrepository")
                .addAnnotatedClass(RdfDocumentContainer.class);
    }
}
