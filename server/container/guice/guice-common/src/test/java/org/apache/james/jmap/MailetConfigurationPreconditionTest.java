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

package org.apache.james.jmap;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.utils.FileConfigurationProvider;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class MailetConfigurationPreconditionTest {
    private JMAPModule.MailetConfigurationPrecondition testee;
    private FileConfigurationProvider fileConfigurationProvider;

    @Before
    public void setUp() throws Exception {
        fileConfigurationProvider = new FileConfigurationProvider(null, "");
        testee = new JMAPModule.MailetConfigurationPrecondition(fileConfigurationProvider);
    }

    @Test
    public void validConfigurationShouldNotThrow() throws Exception {
        testee.performChecks(
            fileConfigurationProvider.getConfiguration(
                ClassLoader.getSystemResourceAsStream("valid_mailetcontainer.xml"),
                Lists.newArrayList()));
    }

    @Test(expected = ConfigurationException.class)
    public void missingBccConfigurationShouldThrow() throws Exception {
        testee.performChecks(
            fileConfigurationProvider.getConfiguration(
                ClassLoader.getSystemResourceAsStream("missing_bcc_mailetcontainer.xml"),
                Lists.newArrayList()));
    }

    @Test(expected = ConfigurationException.class)
    public void missingVacationConfigurationShouldThrow() throws Exception {
        testee.performChecks(
            fileConfigurationProvider.getConfiguration(
                ClassLoader.getSystemResourceAsStream("missing_vacation_mailetcontainer.xml"),
                Lists.newArrayList()));
    }
}
