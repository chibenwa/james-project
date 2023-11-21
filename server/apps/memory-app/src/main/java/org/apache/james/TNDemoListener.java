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

package org.apache.james;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;

import io.netty.buffer.Unpooled;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class TNDemoListener implements EventListener.ReactiveGroupEventListener {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static class ConvocationReport {
        private final String messageId;
        private final String username;

        public ConvocationReport(String messageId, String username) {
            this.messageId = messageId;
            this.username = username;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getUsername() {
            return username;
        }
    }

    public static class TNDemoListenerGroup extends Group {

    }

    private final MailboxManager mailboxManager;
    private final HttpClient httpClient;

    @Inject
    public TNDemoListener(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
        this.httpClient = HttpClient.create();
    }

    @Override
    public Group getDefaultGroup() {
        return new TNDemoListenerGroup();
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxEvents.FlagsUpdated) {
            MailboxEvents.FlagsUpdated updated = (MailboxEvents.FlagsUpdated) event;
            MailboxSession mailboxSession = mailboxManager.createSystemSession(updated.getUsername());

            return Mono.from(mailboxManager.getMailboxReactive(updated.getMailboxId(), mailboxSession))
                .flatMap(mailbox -> Mono.from(mailbox.getMessagesReactive(MessageRange.one(updated.getUids().iterator().next()), FetchGroup.FULL_CONTENT, mailboxSession)))
                .filter(Throwing.predicate(message -> message.getLoadedAttachments()
                    .stream()
                    .anyMatch(attachment -> attachment.getName().map("convocation.pdf"::equalsIgnoreCase).orElse(false))))
                .filter(Throwing.predicate(message -> {
                    Message mime4jMessage = parse(message.getHeaders().getInputStream());

                    return mime4jMessage.getFrom().get(0).getAddress().endsWith("@justice.gouv.tn");
                }))
                .flatMap(Throwing.function(messageResult -> {
                    Message mime4jMessage = parse(messageResult.getHeaders().getInputStream());
                    String messageId = mime4jMessage.getMessageId();

                    return report(messageId, event.getUsername());
                }));
        }
        return Mono.empty();
    }

    private Message parse(InputStream inputStream) throws IOException {
        DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
        defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        return defaultMessageBuilder.parseMessage(inputStream);
    }

    private Mono<Void> report(String messageId, Username username) {
        ConvocationReport convocationReport = new ConvocationReport(messageId, username.asString());
        try {
            String s = OBJECT_MAPPER.writeValueAsString(convocationReport);

            return httpClient
                .baseUrl("http://172.17.0.2/convocations/read-receipts")
                .post()
                .send(Mono.just(Unpooled.wrappedBuffer(s.getBytes(StandardCharsets.UTF_8))))
                .response()
                .then()
                .doFinally(any -> System.out.println("Signaled reading of " + messageId + " by " + username.asString()));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
