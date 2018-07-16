/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mpt.imapmailbox.external.james;

import static org.apache.james.mpt.imapmailbox.external.james.host.ExternalJamesHostSystem.ENV_JAMES_ADDRESS;
import static org.apache.james.mpt.imapmailbox.external.james.host.ExternalJamesHostSystem.ENV_JAMES_IMAP_PORT;

import org.apache.james.mpt.api.ImapHostSystem;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class JamesDeploymentValidationTest extends DeploymentValidation {

    private ImapHostSystem system;

    @BeforeClass
    public static void checks() {
        Assume.assumeNotNull(System.getenv(ENV_JAMES_ADDRESS), System.getenv(ENV_JAMES_IMAP_PORT));
    }

    @Override
    @Before
    public void setUp() throws Exception {
        Injector injector = Guice.createInjector(new ExternalJamesModule());
        system = injector.getInstance(ImapHostSystem.class);
        system.beforeTest();
        super.setUp();
    }

    @Override
    protected ImapHostSystem createImapHostSystem() {
        return system;
    }
    
    @After
    public void tearDown() throws Exception {
        system.afterTest();
    }

    
}
