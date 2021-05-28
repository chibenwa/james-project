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

package org.apache.james.transport.mailets;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.io.File;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.SenderIs;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class SenderIsMatcherTest {
    private static final String OTHER_DOMAIN = "other.com";
    private static final String FROM = "fromuser@" + OTHER_DOMAIN;
    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;
    private static final String BOUNCE_SENDER = "bounce.sender@" + DEFAULT_DOMAIN;
    private static final MailRepositoryUrl CUSTOM_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/custom/");

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    
    private TemporaryJamesServer jamesServer;

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.defaultMailetContainerConfiguration()
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(SenderIs.class)
                        .matcherCondition(FROM)
                        .mailet(ToProcessor.class)
                        .addProperty("processor", ProcessorConfiguration.TRANSPORT_PROCESSOR))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))
                    .build())
                .putProcessor(CommonProcessors.transport());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addDomain(OTHER_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_SENDER, PASSWORD);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void senderIsAndToProcessorShouldWork() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void senderIsShouldRejectNonMatchingSender() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(RECIPIENT, PASSWORD)
            .sendMessage(RECIPIENT, RECIPIENT);

        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);
        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }
}
