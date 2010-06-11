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

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;

import org.apache.derby.jdbc.EmbeddedDataSource;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Bruno Harbulot
 * 
 */
public class HibernateTest {
    @Test
    public void testHibernateAnnotation() throws Exception {
        AnnotationConfiguration configuration = new AnnotationConfiguration()
                .addPackage("uk.ac.manchester.rcs.bruno.webidrepository")
                .addAnnotatedClass(RdfDocumentContainer.class);
        configuration.setProperty("hibernate.connection.url",
                "jdbc:derby:target/testdb;create=true");
        configuration.setProperty("hibernate.connection.driver_class",
                "org.apache.derby.jdbc.EmbeddedDriver");
        configuration.setProperty("hibernate.dialect",
                "org.hibernate.dialect.DerbyDialect");
        configuration.setProperty("hibernate.show_sql", "true");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create");
        SessionFactory sessionFactory = configuration.buildSessionFactory();

        String id = "http://dummy.example/myfoafdoc/";
        String content = "<http://dummy.example/myfoafdoc/#me> rdf:type foaf:Person";

        RdfDocumentContainer foafDoc = new RdfDocumentContainer();
        foafDoc.setId(id);
        foafDoc.setRdfContent(content);

        Session session = sessionFactory.openSession();
        session.beginTransaction();
        session.saveOrUpdate(foafDoc);
        session.getTransaction().commit();
        session.close();

        session = sessionFactory.openSession();
        session.beginTransaction();
        foafDoc = (RdfDocumentContainer) session.load(
                RdfDocumentContainer.class, id);
        Assert.assertEquals(content, foafDoc.getRdfContent());
        session.getTransaction().commit();
        session.close();
    }

    @Test
    public void testDataSource() throws Exception {
        System.setProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                "org.mortbay.naming.InitialContextFactory");

        Context ctx = null;
        try {
            ctx = (Context) new InitialContext().lookup("java:comp");
            try {
                ctx = (Context) ctx.lookup("env");
            } catch (NameNotFoundException e) {
                ctx = ctx.createSubcontext("env");
            }
            try {
                ctx = (Context) ctx.lookup("jdbc");
            } catch (NameNotFoundException e) {
                ctx = ctx.createSubcontext("jdbc");
            }
            EmbeddedDataSource ds = new EmbeddedDataSource();
            ds.setDatabaseName("target/testdb");
            ds.setCreateDatabase("true");
            ctx.rebind("testDS", ds);
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }

        AnnotationConfiguration configuration = new AnnotationConfiguration()
                .addPackage("uk.ac.manchester.rcs.bruno.webidrepository")
                .addAnnotatedClass(RdfDocumentContainer.class);
        configuration.setProperty("hibernate.connection.datasource",
                "java:comp/env/jdbc/testDS");
        configuration.setProperty("hibernate.show_sql", "true");
        configuration.setProperty("hibernate.hbm2ddl.auto", "update");
        SessionFactory sessionFactory = configuration.buildSessionFactory();

        String id = "http://dummy.example/myfoafdoc/";
        String content = "<http://dummy.example/myfoafdoc/#me> rdf:type foaf:Person";

        RdfDocumentContainer foafDoc = new RdfDocumentContainer();
        foafDoc.setId(id);
        foafDoc.setRdfContent(content);

        Session session = sessionFactory.openSession();
        session.beginTransaction();
        session.saveOrUpdate(foafDoc);
        session.getTransaction().commit();
        session.close();

        session = sessionFactory.openSession();
        session.beginTransaction();
        foafDoc = (RdfDocumentContainer) session.load(
                RdfDocumentContainer.class, id);
        Assert.assertEquals(content, foafDoc.getRdfContent());
        session.getTransaction().commit();
        session.close();
    }
}
