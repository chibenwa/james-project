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

package org.apache.james.mailbox.events;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

public interface EventBusContract {
    MailboxListener.MailboxEvent event = mock(MailboxListener.MailboxEvent.class);

    class GroupA extends Group {}
    class GroupB extends Group {}

    MailboxId ID_1 = TestId.of(18);
    MailboxId ID_2 = TestId.of(24);

    EventBus eventBus();

    default MailboxListener newListener() {
        MailboxListener listener = mock(MailboxListener.class);
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
        return listener;
    }

    @Test
    default void listenerGroupShouldReceiveEvents() {
        MailboxListener listener = newListener();

        eventBus().register(listener, new GroupA());

        eventBus().dispatch(event, ImmutableSet.of()).join();

        verify(listener, times(1)).event(any());
    }

    @Test
    default void allListenerGroupShouldReceiveEvents() {
        MailboxListener listener = newListener();
        MailboxListener listener2 = newListener();
        eventBus().register(listener, new GroupA());
        eventBus().register(listener2, new GroupB());

        eventBus().dispatch(event, ImmutableSet.of()).join();

        verify(listener, times(1)).event(any());
        verify(listener2, times(1)).event(any());
    }

    @Test
    default void unregisteredGroupListenerShouldNotReceiveEvents() {
        MailboxListener listener = newListener();
        Registration registration = eventBus().register(listener, new GroupA());

        registration.unregister();

        eventBus().dispatch(event, ImmutableSet.of()).join();
        verifyZeroInteractions(listener);
    }

    @Test
    default void registerShouldThrowWhenAGroupIsAlreadyUsed() {
        MailboxListener listener = newListener();
        MailboxListener listener2 = newListener();

        eventBus().register(listener, new GroupA());

        assertThatThrownBy(() -> eventBus().register(listener2, new GroupA()))
            .isInstanceOf(GroupAlreadyRegistered.class);
    }

    @Test
    default void registerShouldNotThrowOnAnUnregisteredGroup() {
        MailboxListener listener = newListener();
        MailboxListener listener2 = newListener();

        eventBus().register(listener, new GroupA()).unregister();

        assertThatCode(() -> eventBus().register(listener2, new GroupA()))
            .doesNotThrowAnyException();
    }

    @Test
    default void dispatchShouldNotImpactListenerRegisteredWhenEmpty() {
        MailboxListener listener = newListener();
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1));

        eventBus().dispatch(event, ImmutableSet.of()).join();

        verifyZeroInteractions(listener);
    }

    @Test
    default void dispatchShouldNotImpactListenerRegisteredOnOtherKeys() {
        MailboxListener listener = newListener();
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1));

        eventBus().dispatch(event, ImmutableSet.of(new MailboxIdRegistrationKey(ID_2))).join();

        verifyZeroInteractions(listener);
    }

    @Test
    default void dispatchShouldImpactListenerRegistered() {
        MailboxListener listener = newListener();
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1));

        eventBus().dispatch(event, ImmutableSet.of(new MailboxIdRegistrationKey(ID_1))).join();

        verify(listener, times(1)).event(any());
    }

    @Test
    default void dispatchShouldImpactOnlyListenerRegistered() {
        MailboxListener listener = newListener();
        MailboxListener listener2 = newListener();
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1));
        eventBus().register(listener2, new MailboxIdRegistrationKey(ID_2));

        eventBus().dispatch(event, ImmutableSet.of(new MailboxIdRegistrationKey(ID_1))).join();

        verify(listener, times(1)).event(any());
        verifyZeroInteractions(listener2);
    }

    @Test
    default void registerShouldAllowDuplicatedRegistration() {
        MailboxListener listener = newListener();
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1));
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1));

        eventBus().dispatch(event, ImmutableSet.of(new MailboxIdRegistrationKey(ID_1))).join();

        verify(listener, times(1)).event(any());
    }

    @Test
    default void unregisterShouldNotRemoveDoubleRegisteredListener() {
        MailboxListener listener = newListener();
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1));
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1)).unregister();

        eventBus().dispatch(event, ImmutableSet.of(new MailboxIdRegistrationKey(ID_1))).join();

        verify(listener, times(1)).event(any());
    }

    @Test
    default void callingAllUnregisterMethodShouldUnregisterTheListener() {
        MailboxListener listener = newListener();
        Registration registration = eventBus().register(listener, new MailboxIdRegistrationKey(ID_1));
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1)).unregister();
        registration.unregister();

        eventBus().dispatch(event, ImmutableSet.of(new MailboxIdRegistrationKey(ID_1))).join();

        verifyZeroInteractions(listener);
    }

    @Test
    default void unregisterShouldHaveNoImpactWhenCalledOnDifferentKeys() {
        MailboxListener listener = newListener();
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1));
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_2)).unregister();

        eventBus().dispatch(event, ImmutableSet.of(new MailboxIdRegistrationKey(ID_1))).join();

        verify(listener, times(1)).event(any());
    }

    @Test
    default void dispatchShouldAcceptSeveralKeys() {
        MailboxListener listener = newListener();
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1));

        eventBus().dispatch(event, ImmutableSet.of(new MailboxIdRegistrationKey(ID_1), new MailboxIdRegistrationKey(ID_2))).join();

        verify(listener, times(1)).event(any());
    }

    @Test
    default void dispatchShouldCallListenerOnceWhenSeveralKeysMatching() {
        MailboxListener listener = newListener();
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1));
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_2));

        eventBus().dispatch(event, ImmutableSet.of(new MailboxIdRegistrationKey(ID_1), new MailboxIdRegistrationKey(ID_2))).join();

        verify(listener, times(1)).event(any());
    }

    @Test
    default void dispatchShouldNotImpactUnregisteredListener() {
        MailboxListener listener = newListener();
        eventBus().register(listener, new MailboxIdRegistrationKey(ID_1)).unregister();

        eventBus().dispatch(event, ImmutableSet.of(new MailboxIdRegistrationKey(ID_1))).join();

        verifyZeroInteractions(listener);
    }

    @Test
    default void dispatchShouldCallSynchronousListener() {
        MailboxListener listener = newListener();

        eventBus().register(listener, new GroupA());

        eventBus().dispatch(event, ImmutableSet.of()).join();

        verify(listener, times(1)).event(any());
    }

    @Test
    default void dispatchShouldNotBlockAsynchronousListener() {
        MailboxListener listener = newListener();
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.await();
            return null;
        }).when(listener).event(event);

        assertTimeout(Duration.ofSeconds(2),
            () -> {
                eventBus().dispatch(event, ImmutableSet.of()).join();
                latch.countDown();
            });
    }
}
