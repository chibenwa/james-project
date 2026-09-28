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

package org.apache.james.queue.activemq;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.Mails;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.jms.JMSCacheableMailQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.base.Strings;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * JAMES-4192: delayed mails sitting in the queue must not prevent ready mails from being dequeued.
 *
 * The broker mirrors {@link org.apache.james.queue.activemq.EmbeddedActiveMQ}: persistent journal, InVM,
 * consumerWindowSize=0. The only difference is the global max size: in production it defaults to half
 * the heap, here it is lowered so that the queue starts paging with a reasonable amount of mails.
 */
class ActiveMQMailQueueDelayedMailsTest {
    private static final int SERVER_ID = 4192;
    private static final long GLOBAL_MAX_SIZE = 1024 * 1024;
    private static final String BODY_50_KB = Strings.repeat("0123456789abcdef\r\n", 50 * 1024 / 18);

    @TempDir
    File dataDirectory;

    private EmbeddedActiveMQ broker;
    private JMSCacheableMailQueue mailQueue;
    private MailQueueName queueName;

    @BeforeEach
    void setUp() throws Exception {
        broker = new EmbeddedActiveMQ();
        broker.setConfiguration(new ConfigurationImpl()
            .setSecurityEnabled(false)
            .setJMXManagementEnabled(false)
            .setPersistenceEnabled(true)
            .setJournalDirectory(dataDirectory.getAbsolutePath() + "/journal")
            .setBindingsDirectory(dataDirectory.getAbsolutePath() + "/bindings")
            .setLargeMessagesDirectory(dataDirectory.getAbsolutePath() + "/largemessages")
            .setPagingDirectory(dataDirectory.getAbsolutePath() + "/paging")
            .setGlobalMaxSize(GLOBAL_MAX_SIZE)
            .addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName(),
                Map.of(TransportConstants.SERVER_ID_PROP_NAME, SERVER_ID)))
            .setName("james"));
        broker.start();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://" + SERVER_ID);
        connectionFactory.setConsumerWindowSize(0);
        queueName = MailQueueName.of("outgoing");
        mailQueue = new JMSCacheableMailQueue(connectionFactory, new RawMailQueueItemDecoratorFactory(), queueName,
            new RecordingMetricFactory(), new NoopGaugeRegistry());
    }

    @AfterEach
    void tearDown() throws Exception {
        mailQueue.dispose();
        broker.stop();
    }

    @Test
    void readyMailShouldBeDequeuedWhenFewDelayedMailsAreEnqueuedBefore() throws Exception {
        enqueueDelayedMails(10);
        mailQueue.enQueue(Mails.defaultMail().name("ready").build());

        assertThat(isPaging()).isFalse();
        assertThat(dequeueOneName()).isEqualTo("ready");
    }

    @Test
    void readyMailShouldBeDequeuedWhenManyDelayedMailsAreEnqueuedBefore() throws Exception {
        // ~50 MB of delayed mails: more than the global max size and than the default max-read-page-bytes
        enqueueDelayedMails(1000);
        mailQueue.enQueue(Mails.defaultMail().name("ready").build());

        assertThat(isPaging()).isTrue();
        assertThat(dequeueOneName()).isEqualTo("ready");
    }

    private void enqueueDelayedMails(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            mailQueue.enQueue(Mails.defaultMail().name("delayed-" + i).mimeMessage(bigMessage()).build(), 1, TimeUnit.HOURS);
        }
    }

    private String dequeueOneName() {
        return Flux.from(mailQueue.deQueue())
            .next()
            .timeout(Duration.ofSeconds(30), Mono.empty())
            .blockOptional()
            .map(item -> item.getMail().getName())
            .orElse(null);
    }

    private boolean isPaging() throws Exception {
        return broker.getActiveMQServer().getPagingManager()
            .getPageStore(SimpleString.of(queueName.asString()))
            .isPaging();
    }

    private static MimeMessage bigMessage() throws MessagingException {
        return MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("delayed")
            .setText(BODY_50_KB)
            .build();
    }
}
