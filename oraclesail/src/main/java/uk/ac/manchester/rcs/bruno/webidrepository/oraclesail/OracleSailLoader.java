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
package uk.ac.manchester.rcs.bruno.webidrepository.oraclesail;

import java.sql.SQLException;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import oracle.spatial.rdf.client.sesame.OraclePool;
import oracle.spatial.rdf.client.sesame.OracleSailStore;

import org.openrdf.sail.Sail;
import org.restlet.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.manchester.rcs.bruno.webidrepository.SailLoader;

/**
 * @author Bruno Harbulot (Bruno.Harbulot@manchester.ac.uk)
 * 
 */
public class OracleSailLoader implements SailLoader {
    private final static Logger LOGGER = LoggerFactory
            .getLogger(OracleSailLoader.class);

    public Sail loadSail(Context context) {
        String dataSourceName = context.getParameters().getFirstValue(
                "oracle_sail_datasource");
        String model = context.getParameters().getFirstValue(
                "oracle_sail_model");
        DataSource ds = (DataSource) context.getAttributes()
                .get(dataSourceName);
        if (ds == null) {
            javax.naming.Context jndiContext = null;
            javax.naming.Context envCtx = null;
            try {
                jndiContext = new InitialContext();
                envCtx = (javax.naming.Context) jndiContext
                        .lookup("java:comp/env");
                ds = (DataSource) envCtx.lookup(dataSourceName);
                context.getAttributes().put(dataSourceName, ds);
            } catch (NamingException e) {
                LOGGER.error(String.format(
                        "Unable to load DataSource using JNDI (name: %s).",
                        dataSourceName), e);
                throw new RuntimeException(e);
            } finally {
                try {
                    try {
                        if (envCtx != null) {
                            envCtx.close();
                        }
                    } finally {
                        if (jndiContext != null) {
                            jndiContext.close();
                        }
                    }
                } catch (NamingException e) {
                    LOGGER.error("Unable to close JNDI context.", e);
                    throw new RuntimeException(e);
                }
            }
        }
        if (ds == null) {
            String errorMsg = String.format("No DataSource found (name: %s).",
                    dataSourceName);
            LOGGER.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        try {
            ds = OraclePool
                    .getOracleDataSource(
                            "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=oracle.vidar.ngs.manchester.ac.uk)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=sand)))",
                            "nanocmos_exp2", "bruno");
            OraclePool pool = new OraclePool(ds);
            OracleSailStore sail = new OracleSailStore(pool, model);
            return sail;
        } catch (SQLException e) {
            LOGGER.error("Unable to initialise OracleSailStore.", e);
            throw new RuntimeException(e);
        }
    }
}
