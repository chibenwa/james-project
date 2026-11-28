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
import static org.apache.james.events.EventListener.wrapReactive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AMQP;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.Sender;

class KeyRegistrationHandlerTest {
    private static final NamingStrategy NAMING_STRATEGY = new DefaultNamingStrategy(new EventBusName("test"));
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private Sender sender;
    private LocalListenerRegistry registry;
    private KeyRegistrationHandler handler;

    @BeforeEach
    void setUp() {
        sender = mock(Sender.class);
        when(sender.unbind(any(BindingSpecification.class))).thenReturn(Mono.empty());
        registry = new LocalListenerRegistry();
        handler = new KeyRegistrationHandler(NAMING_STRATEGY, EventBusId.random(), null, sender, null, null, registry, null,
            new RabbitMQEventBus.Configurations(null, RetryBackoffConfiguration.FAST), new RecordingMetricFactory());
    }

    @Test
    void registerShouldAddTheListenerOnlyOnSubscription() {
        handler.register(newListener(), KEY_1);

        assertThat(registry.getLocalListeners(KEY_1)).isEmpty();
    }

    @Test
    void registrationCancelledDuringTheBindShouldRemoveTheListener() {
        when(sender.bind(any(BindingSpecification.class))).thenReturn(Mono.never());

        Disposable subscription = handler.register(newListener(), KEY_1).subscribe();
        Awaitility.await().atMost(TIMEOUT).untilAsserted(() -> verify(sender).bind(any(BindingSpecification.class)));
        subscription.dispose();

        assertThat(registry.getLocalListeners(KEY_1)).isEmpty();
    }

    @Test
    void registrationWhoseBindFailsShouldRemoveTheListener() {
        when(sender.bind(any(BindingSpecification.class))).thenReturn(Mono.error(new RuntimeException("broker unavailable")));

        assertThatThrownBy(() -> handler.register(newListener(), KEY_1).block())
            .isInstanceOf(RuntimeException.class);

        assertThat(registry.getLocalListeners(KEY_1)).isEmpty();
    }

    @Test
    void registrationAfterACancelledOneShouldBindTheKeyAgain() {
        when(sender.bind(any(BindingSpecification.class))).thenReturn(Mono.never());
        Disposable subscription = handler.register(newListener(), KEY_1).subscribe();
        Awaitility.await().atMost(TIMEOUT).untilAsserted(() -> verify(sender).bind(any(BindingSpecification.class)));
        subscription.dispose();

        when(sender.bind(any(BindingSpecification.class))).thenReturn(Mono.just(mock(AMQP.Queue.BindOk.class)));
        handler.register(newListener(), KEY_1).block();

        verify(sender, atLeast(2)).bind(any(BindingSpecification.class));
        assertThat(registry.getLocalListeners(KEY_1)).hasSize(1);
    }

    @Test
    void cancellingTheFirstRegistrationShouldBindTheKeyForTheOtherListeners() {
        when(sender.bind(any(BindingSpecification.class))).thenReturn(Mono.never());
        Disposable firstSubscription = handler.register(newListener(), KEY_1).subscribe();
        Awaitility.await().atMost(TIMEOUT).untilAsserted(() -> verify(sender).bind(any(BindingSpecification.class)));
        Registration second = handler.register(newListener(), KEY_1).block();

        when(sender.bind(any(BindingSpecification.class))).thenReturn(Mono.just(mock(AMQP.Queue.BindOk.class)));
        firstSubscription.dispose();

        Awaitility.await().atMost(TIMEOUT).untilAsserted(() -> verify(sender, atLeast(2)).bind(any(BindingSpecification.class)));
        assertThat(registry.getLocalListeners(KEY_1)).hasSize(1);
        assertThat(second).isNotNull();
    }

    @Test
    void completedRegistrationShouldKeepTheListenerUntilUnregistered() {
        when(sender.bind(any(BindingSpecification.class))).thenReturn(Mono.just(mock(AMQP.Queue.BindOk.class)));

        Registration registration = handler.register(newListener(), KEY_1).block();
        assertThat(registry.getLocalListeners(KEY_1)).hasSize(1);

        Mono.from(registration.unregister()).block();
        assertThat(registry.getLocalListeners(KEY_1)).isEmpty();
    }

    private EventListener.ReactiveEventListener newListener() {
        return wrapReactive(EventBusTestFixture.newListener());
    }
}
