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

package org.apache.james.imap.processor;

import static org.apache.james.imap.ImapFixture.TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.james.events.EventListener;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.IdleRequest;
import org.apache.james.imap.message.response.ExpungeResponse;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.NullableMessageSequenceNumber;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

class IdleProcessorLifecycleTest {
    private static final MessageUid EXPUNGED_UID = MessageUid.of(3);

    private static class TrackingImapSession extends FakeImapSession {
        final Deque<ImapLineHandler> handlers = new ArrayDeque<>();
        final AtomicInteger popCount = new AtomicInteger();
        final Queue<Runnable> eventLoopTasks = new ConcurrentLinkedQueue<>();
        boolean triggerCallbackDuringPush = false;
        Consumer<FakeImapSession> onPush;

        @Override
        public Scheduler eventLoopScheduler() {
            return Schedulers.fromExecutor(eventLoopTasks::add);
        }

        void runEventLoopTasks() {
            Stream.generate(eventLoopTasks::poll)
                .takeWhile(Objects::nonNull)
                .forEach(Runnable::run);
        }

        void done() {
            Mono.from(handlers.peek().onLine(this, "DONE\r\n".getBytes(StandardCharsets.US_ASCII))).block();
        }

        @Override
        public ImapProcessor.Responder threadSafe(ImapProcessor.Responder responder) {
            return responder;
        }

        @Override
        public void pushLineHandler(ImapLineHandler lineHandler) {
            handlers.push(lineHandler);
            if (triggerCallbackDuringPush) {
                Mono.from(lineHandler.onLine(this, "DONE\r\n".getBytes(StandardCharsets.US_ASCII))).block();
            }
            if (onPush != null) {
                try {
                    onPush.accept(this);
                } catch (RuntimeException e) {
                    handlers.pop();
                    throw e;
                }
            }
        }

        @Override
        public void popLineHandler() {
            popCount.incrementAndGet();
            if (!handlers.isEmpty()) {
                handlers.pop();
            }
        }
    }

    private static class RecordingResponder implements ImapProcessor.Responder {
        private final List<ImapResponseMessage> responses = new CopyOnWriteArrayList<>();

        @Override
        public void respond(ImapResponseMessage message) {
            responses.add(message);
        }

        @Override
        public void flush() {
        }

        public List<ImapResponseMessage> getResponses() {
            return responses;
        }
    }

    @Test
    void earlyCallbackDuringPushLineHandlerShouldPopHandlerExactlyOnce() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        TrackingImapSession session = new TrackingImapSession();
        session.triggerCallbackDuringPush = true;

        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        testee.processRequestReactive(new IdleRequest(TAG), session, new RecordingResponder()).block();

        // popLineHandler was invoked exactly once for the IDLE handler, base handler preserved
        assertThat(session.popCount.get()).isEqualTo(1);
        assertThat(session.handlers).containsExactly(baseHandler);
    }

    @Test
    void midPushDisconnectOrCleanupShouldPopHandlerExactlyOnceWhenPushCompletes() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        TrackingImapSession session = new TrackingImapSession();
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        session.selected(selectedMailbox).block();

        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        session.onPush = s -> {
            ImapLineHandler idleHandler = session.handlers.peek();
            Mono.from(idleHandler.onLine(s, "DONE\r\n".getBytes(StandardCharsets.US_ASCII))).block();
        };

        testee.processRequestReactive(new IdleRequest(TAG), session, new RecordingResponder()).block();

        assertThat(session.popCount.get()).isEqualTo(1);
        assertThat(session.handlers).containsExactly(baseHandler);
    }

    @Test
    void exceptionDuringRegisterIdleShouldAttemptListenerCleanup() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        TrackingImapSession session = new TrackingImapSession();
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        session.selected(selectedMailbox).block();

        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        doThrow(new RuntimeException("Mailbox error"))
            .when(selectedMailbox).registerIdle(any());

        RecordingResponder responder = new RecordingResponder();
        testee.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        ArgumentCaptor<EventListener.ReactiveEventListener> captor =
            ArgumentCaptor.forClass(EventListener.ReactiveEventListener.class);
        verify(selectedMailbox).unregisterIdle(captor.capture());
        assertThat(captor.getValue()).isNotNull();
        assertThat(session.popCount.get()).isZero();
        assertThat(session.handlers).containsExactly(baseHandler);
        assertThat(responder.getResponses()).hasSize(1);
        assertThat(responder.getResponses().get(0))
            .isInstanceOf(StatusResponse.class);
        StatusResponse statusResponse = (StatusResponse) responder.getResponses().get(0);
        assertThat(statusResponse.getServerResponseType())
            .isEqualTo(StatusResponse.Type.NO);
    }

    @Test
    void exceptionDuringUnregisterIdleShouldStillCleanUpLineHandlerStateAndSink() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        TrackingImapSession session = new TrackingImapSession();
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        session.selected(selectedMailbox).block();

        doThrow(new RuntimeException("Unregister failed"))
            .when(selectedMailbox).unregisterIdle(any());

        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        session.triggerCallbackDuringPush = true;

        RecordingResponder responder = new RecordingResponder();
        testee.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        assertThat(session.popCount.get()).isEqualTo(1);
        assertThat(session.handlers).containsExactly(baseHandler);
        ArgumentCaptor<EventListener.ReactiveEventListener> captor =
            ArgumentCaptor.forClass(EventListener.ReactiveEventListener.class);
        verify(selectedMailbox).unregisterIdle(captor.capture());
        assertThat(captor.getValue()).isNotNull();
        assertThat(responder.getResponses()).hasSize(1);
    }

    @Test
    void unregisterIdleDuringPushFailureShouldSucceed() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        TrackingImapSession session = new TrackingImapSession();
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        session.selected(selectedMailbox).block();

        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        session.onPush = s -> {
            throw new RuntimeException("Push failed");
        };

        RecordingResponder responder = new RecordingResponder();
        testee.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        ArgumentCaptor<EventListener.ReactiveEventListener> captor =
            ArgumentCaptor.forClass(EventListener.ReactiveEventListener.class);
        verify(selectedMailbox).unregisterIdle(captor.capture());
        assertThat(captor.getValue()).isNotNull();
        assertThat(session.popCount.get()).isZero();
        assertThat(session.handlers).containsExactly(baseHandler);
        assertThat(responder.getResponses()).hasSize(1);
        assertThat(responder.getResponses().get(0))
            .isInstanceOf(StatusResponse.class);
        StatusResponse statusResponse = (StatusResponse) responder.getResponses().get(0);
        assertThat(statusResponse.getServerResponseType())
            .isEqualTo(StatusResponse.Type.NO);
    }

    @Test
    void exceptionDuringPopLineHandlerShouldStillCompleteSinkAndPreservePipeline() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        TrackingImapSession session = new TrackingImapSession() {
            @Override
            public void popLineHandler() {
                super.popLineHandler();
                throw new RuntimeException("Faulty popLineHandler");
            }
        };
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        session.selected(selectedMailbox).block();

        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        session.triggerCallbackDuringPush = true;

        RecordingResponder responder = new RecordingResponder();
        testee.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        assertThat(session.popCount.get()).isEqualTo(1);
        ArgumentCaptor<EventListener.ReactiveEventListener> captor =
            ArgumentCaptor.forClass(EventListener.ReactiveEventListener.class);
        verify(selectedMailbox).unregisterIdle(captor.capture());
        assertThat(captor.getValue()).isNotNull();
        assertThat(responder.getResponses()).hasSize(1);
        assertThat(responder.getResponses().get(0))
            .isInstanceOf(StatusResponse.class);
        StatusResponse statusResponse = (StatusResponse) responder.getResponses().get(0);
        assertThat(statusResponse.getServerResponseType())
            .isEqualTo(StatusResponse.Type.NO);
    }

    @Test
    void expungeNotifiedBeforeDoneShouldNotBeSentAfterTheTaggedOk() {
        TrackingImapSession session = new TrackingImapSession();
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        RecordingResponder responder = new RecordingResponder();
        EventListener.ReactiveEventListener listener = idleWithPendingExpunge(session, selectedMailbox, responder);

        Mono.from(listener.reactiveEvent(mock(Expunged.class))).subscribe();
        session.done();
        session.runEventLoopTasks();

        assertThat(responder.getResponses()).noneMatch(ExpungeResponse.class::isInstance);
        assertThat(responder.getResponses()).last().isInstanceOf(StatusResponse.class);
        verify(selectedMailbox, never()).remove(any());
    }

    @Test
    void expungePushedBeforeDoneShouldBeSentBeforeTheTaggedOk() {
        TrackingImapSession session = new TrackingImapSession();
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        RecordingResponder responder = new RecordingResponder();
        EventListener.ReactiveEventListener listener = idleWithPendingExpunge(session, selectedMailbox, responder);

        Mono.from(listener.reactiveEvent(mock(Expunged.class))).subscribe();
        session.runEventLoopTasks();
        session.done();

        List<ImapResponseMessage> responses = responder.getResponses();
        assertThat(responses.get(responses.size() - 2)).isInstanceOf(ExpungeResponse.class);
        assertThat(responses).last().isInstanceOf(StatusResponse.class);
        verify(selectedMailbox).remove(EXPUNGED_UID);
    }

    private EventListener.ReactiveEventListener idleWithPendingExpunge(TrackingImapSession session, SelectedMailbox selectedMailbox,
                                                                       RecordingResponder responder) {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());
        session.selected(selectedMailbox).block();
        testee.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        ArgumentCaptor<EventListener.ReactiveEventListener> captor =
            ArgumentCaptor.forClass(EventListener.ReactiveEventListener.class);
        verify(selectedMailbox).registerIdle(captor.capture());
        // Another session expunged the message: the selected mailbox recorded it, the listener is notified
        when(selectedMailbox.expungedUids()).thenReturn(List.of(EXPUNGED_UID));
        when(selectedMailbox.remove(EXPUNGED_UID)).thenReturn(NullableMessageSequenceNumber.of(3));
        return captor.getValue();
    }
}
