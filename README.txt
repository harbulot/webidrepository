This is the WebID Repository for the JISC Access and Identity Management 
using Social Networking Technologies.

It uses the Corypha framework (small developed as part of this project),
which is based on Restlet and provides features such as templates and
persistence.


The Webapp module is intended to be run within a servlet contain (from its war
file). The configuration is based on JNDI.

Because this webapp issues certificates, it needs a keystore (with a private key
with which to sign these certificates). This can be configured within
server.xml in Tomcat (for example) as follows:




<Context docBase="webidrepository-webapp" path="/webidrepository-webapp" reloadable="true" source="org.eclipse.jst.jee.server:webidrepository-webapp">
    <Environment name="webiddirectory/signingKeystorePath" override="false" type="java.lang.String" value="/path/to/keystore.p12"/>
    <Environment name="webiddirectory/signingKeystoreType" override="false" type="java.lang.String" value="PKCS12"/>
    <Environment name="webiddirectory/signingKeystorePassword" override="false" type="java.lang.String" value="testtest"/>
    <Environment name="webiddirectory/issuerName" override="false" type="java.lang.String" value="CN=FOAFSSLTEST"/>

    <Environment name="hibernate/connection.datasource" override="false" type="java.lang.String" value="java:comp/env/jdbc/webiddirectoryDS"/>
    <Environment name="hibernate/dialect" override="false" type="java.lang.String" value="org.hibernate.dialect.Oracle10gDialect"/>
    <Environment name="hibernate/show_sql" override="false" type="java.lang.String" value="true"/>
    <Environment name="hibernate/hbm2ddl.auto" override="false" type="java.lang.String" value="update"/>

    <Resource auth="Container" driverClassName="oracle.jdbc.OracleDriver" maxActive="5" maxIdle="3" maxWait="-1" name="jdbc/webiddirectoryDS" password="....." type="javax.sql.DataSource" url="jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=......)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=....)))" username="......"/>
</Context>


Note that you would need to adapt to Hibernate driverClassName attribute for 
your JDBC driver and the 'hibernate/dialect' environment value to the fully
qualified name of the hibernate dialect matching this driver.
You may need to download the JDBC driver you want to use (the one from Oracle
could not be bundled for licensing reasons) and place it in the lib
directory of your servlet container.