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

import javax.naming.Binding;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;

import org.eclipse.jetty.plus.jndi.EnvEntry;
import org.junit.Ignore;

/**
 * @author Bruno Harbulot
 * 
 */
@Ignore
public class JndiTest {
    public static void main(String[] args) throws Throwable {
        System.setProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                org.eclipse.jetty.jndi.InitialContextFactory.class.getName());

        new EnvEntry("java:comp/env/test/testInt", Integer.valueOf(1000), true);
        new EnvEntry("java:comp/env/test/testStr", "Test String", true);

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
                ctx = (javax.naming.Context) envCtx.lookup("test");
            } catch (NameNotFoundException e) {
                ctx = envCtx.createSubcontext("test");
            }

            System.out.println("testInt: " + ctx.lookup("testInt"));
            System.out.println("testStr: " + ctx.lookup("testStr"));

        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }

        try {
            ctx = new javax.naming.InitialContext();
            javax.naming.Context env = (javax.naming.Context) ctx
                    .lookup("java:comp/env");
            NamingEnumeration<Binding> bindings = null;
            bindings = env.listBindings("test");

            if (bindings != null) {
                while (bindings.hasMore()) {
                    Binding binding = bindings.next();
                    Object object = binding.getObject();
                    if (object != null) {
                        System.out.println(String.format(
                                "Setting hibernate property: %s = %s", binding
                                        .getName(), object.toString()));
                    }
                }
            }
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }
}
