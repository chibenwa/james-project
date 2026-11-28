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

package org.apache.james.events;

import static org.apache.james.events.EventBusTestFixture.KEY_1;
import static org.apache.james.events.EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION;
import static org.apache.james.events.EventBusTestFixture.newListener;
import static org.apache.james.events.NamingStrategy.MAILBOX_EVENT_NAMING_STRATEGY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.james.events.EventBusTestFixture.TestRegistrationKeyFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.Sender;

class KeyRegistrationHandlerTest {
    private static final String BIND = "bind";
    private static final String UNBIND = "unbind";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final List<String> brokerOperations = new CopyOnWriteArrayList<>();
    private final CountDownLatch unbindReachedBroker = new CountDownLatch(1);
    private final CountDownLatch unbindMayComplete = new CountDownLatch(1);
    private LocalListenerRegistry localListenerRegistry;
    private KeyRegistrationHandler keyRegistrationHandler;

    @BeforeEach
    void setUp() {
        Sender sender = mock(Sender.class);
        when(sender.bind(any())).thenAnswer(invocation -> Mono.fromRunnable(() -> brokerOperations.add(BIND)));
        when(sender.unbind(any())).thenAnswer(invocation -> Mono.fromRunnable(this::slowUnbind));

        localListenerRegistry = new LocalListenerRegistry();
        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        keyRegistrationHandler = new KeyRegistrationHandler(MAILBOX_EVENT_NAMING_STRATEGY, EventBusId.random(),
            null, sender, null, RoutingKeyConverter.forFactories(new TestRegistrationKeyFactory()),
            localListenerRegistry, new ListenerExecutor(metricFactory),
            new RabbitMQEventBus.Configurations(null, RETRY_BACKOFF_CONFIGURATION), metricFactory);
    }

    @Test
    void keyShouldStayBoundWhenAListenerRegistersWhileTheLastOneIsUnbinding() throws Exception {
        Registration registrationA = keyRegistrationHandler.register(EventListener.wrapReactive(newListener()), KEY_1).block();
        brokerOperations.clear();

        CompletableFuture<Void> unregisterA = Mono.from(registrationA.unregister()).toFuture();
        assertThat(unbindReachedBroker.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        EventListener.ReactiveEventListener listenerB = EventListener.wrapReactive(newListener());
        keyRegistrationHandler.register(listenerB, KEY_1).block(TIMEOUT);
        unbindMayComplete.countDown();
        unregisterA.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        assertThat(localListenerRegistry.getLocalListeners(KEY_1)).containsExactly(listenerB);
        assertThat(brokerOperations).last().isEqualTo(BIND);
    }

    @Test
    void keyShouldBeUnboundWhenNoListenerRegistersWhileTheLastOneIsUnbinding() {
        Registration registrationA = keyRegistrationHandler.register(EventListener.wrapReactive(newListener()), KEY_1).block();
        brokerOperations.clear();
        unbindMayComplete.countDown();

        Mono.from(registrationA.unregister()).block(TIMEOUT);

        assertThat(brokerOperations).containsExactly(UNBIND);
    }

    private void slowUnbind() {
        unbindReachedBroker.countDown();
        try {
            unbindMayComplete.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        brokerOperations.add(UNBIND);
    }
}
