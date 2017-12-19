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

package org.apache.james.smtp;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Strings;
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;

public class SmtpSizeLimitationTest {
    private static final String DEFAULT_DOMAIN = "james.org";
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int SMTP_PORT = 1025;
    private static final String PASSWORD = "secret";

    private static final String JAMES_APACHE_ORG = "james.org";
    private static final String USER = "user@" + JAMES_APACHE_ORG;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private TemporaryJamesServer jamesServer;
    private ConditionFactory calmlyAwait;

    @Before
    public void setup() throws Exception {
        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with()
            .pollInterval(slowPacedPollInterval)
            .and()
            .with()
            .pollDelay(slowPacedPollInterval)
            .await();
    }

    private void createJamesServer(SmtpConfiguration.Builder smtpConfiguration) throws Exception {
        MailetContainer mailetContainer = MailetContainer.builder()
            .postmaster("postmaster@" + DEFAULT_DOMAIN)
            .threads(5)
            .addProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToProcessor.class)
                    .addProperty("processor", ProcessorConfiguration.STATE_TRANSPORT)))
            .addProcessor(CommonProcessors.error())
            .addProcessor(ProcessorConfiguration.transport()
                .enableJmx(true)
                .addMailet(MailetConfiguration.BCC_STRIPPER)
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIsLocal.class)
                    .mailet(LocalDelivery.class))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToProcessor.class)
                    .addProperty("processor", ProcessorConfiguration.STATE_BOUNCES)))
            .addProcessor(CommonProcessors.localAddressError())
            .addProcessor(CommonProcessors.relayDenied())
            .addProcessor(CommonProcessors.bounces())
            .build();
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_ONLY_MODULE)
            .withSmtpConfiguration(smtpConfiguration.build())
            .build(temporaryFolder, mailetContainer);

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(USER, PASSWORD);
    }

    @After
    public void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    public void messageShouldNotBeAcceptedWhenOverSized() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .doNotVerifyIdentity()
            .withMaxMessageSizeInKb(10));

        try (SMTPMessageSender messageSender =
                 SMTPMessageSender.authentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG, USER, PASSWORD)) {

            messageSender.sendMessageWithHeaders(USER, USER, Strings.repeat("Long message", 1024));
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageSendingFailed);
        }
    }

    @Test
    public void messageShouldBeAcceptedWhenNotOverSized() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .doNotVerifyIdentity()
            .withMaxMessageSizeInKb(10));

        try (SMTPMessageSender messageSender =
                 SMTPMessageSender.authentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG, USER, PASSWORD)) {

            messageSender.sendMessageWithHeaders(USER, USER,"Short message");
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
        }
    }
}
