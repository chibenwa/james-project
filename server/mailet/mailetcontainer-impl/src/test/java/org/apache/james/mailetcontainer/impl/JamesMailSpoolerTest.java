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

package org.apache.james.mailetcontainer.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class JamesMailSpoolerTest {
    private static final MailRepositoryUrl ERROR_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/error");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final AtomicInteger subscriptions = new AtomicInteger();
    private final AtomicInteger cancellations = new AtomicInteger();
    private JamesMailSpooler spooler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        MailQueue spool = mock(MailQueue.class);
        when(spool.deQueue()).thenAnswer(any -> Flux.<MailQueueItem>never()
            .doOnSubscribe(subscription -> subscriptions.incrementAndGet())
            .doOnCancel(cancellations::incrementAndGet));
        MailQueueFactory<MailQueue> queueFactory = mock(MailQueueFactory.class);
        when(queueFactory.createQueue(eq(MailQueueFactory.SPOOL), any())).thenReturn(spool);
        MailRepositoryStore mailRepositoryStore = mock(MailRepositoryStore.class);
        when(mailRepositoryStore.select(ERROR_REPOSITORY)).thenReturn(mock(MailRepository.class));

        spooler = new JamesMailSpooler(new RecordingMetricFactory(), new NoopGaugeRegistry(), mail -> { },
            mailRepositoryStore, queueFactory);
        spooler.configure(new JamesMailSpooler.Configuration(1, ERROR_REPOSITORY));
        spooler.init();
        await().atMost(TIMEOUT).until(() -> subscriptions.get() == 1);
    }

    @AfterEach
    void tearDown() {
        spooler.dispose();
    }

    @Test
    void concurrentRestartsShouldNotLeaveAConsumerAliveAfterDispose() throws Exception {
        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> spooler.restart())
            .threadCount(8)
            .operationCount(10)
            .runSuccessfullyWithin(TIMEOUT);

        spooler.dispose();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(liveConsumers()).isZero());
    }

    @Test
    void restartAfterDisposeShouldNotStartAConsumer() {
        spooler.dispose();

        spooler.restart();

        await().during(Duration.ofMillis(500)).atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(subscriptions.get()).isEqualTo(1));
        assertThat(liveConsumers()).isZero();
    }

    private int liveConsumers() {
        return subscriptions.get() - cancellations.get();
    }
}
