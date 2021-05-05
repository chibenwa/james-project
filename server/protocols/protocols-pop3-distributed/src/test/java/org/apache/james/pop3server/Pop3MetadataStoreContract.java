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

package org.apache.james.pop3server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.pop3server.mailbox.Pop3MetadataStore;
import org.apache.james.pop3server.mailbox.Pop3MetadataStore.StatMetadata;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Pop3MetadataStoreContract {

    int SIZE_1 = 234;
    int SIZE_2 = 456;

    Pop3MetadataStore testee();

    MailboxId generateMailboxId();

    MessageId generateMessageId();

    @Test
    default void statShouldReturnEmptyByDefault() {
        assertThat(
            Flux.from(testee()
                .stat(generateMailboxId()))
                .collectList()
                .block())
            .isEmpty();
    }

    @Test
    default void statShouldReturnPreviouslyAddedMetadata() {
        MailboxId mailboxId = generateMailboxId();
        StatMetadata metadata = new StatMetadata(generateMessageId(), SIZE_1);
        Mono.from(testee().add(mailboxId, metadata)).block();

        assertThat(
            Flux.from(testee()
                .stat(mailboxId))
                .collectList()
                .block())
            .containsOnly(metadata);
    }

    @Test
    default void statShouldReturnAllPreviouslyAddedMetadata() {
        MailboxId mailboxId = generateMailboxId();
        StatMetadata metadata1 = new StatMetadata(generateMessageId(), SIZE_1);
        StatMetadata metadata2 = new StatMetadata(generateMessageId(), SIZE_2);
        Mono.from(testee().add(mailboxId, metadata1)).block();
        Mono.from(testee().add(mailboxId, metadata2)).block();

        assertThat(
            Flux.from(testee()
                .stat(mailboxId))
                .collectList()
                .block())
            .containsOnly(metadata1, metadata2);
    }

    @Test
    default void statShouldNotReturnDeletedData() {
        MailboxId mailboxId = generateMailboxId();
        StatMetadata metadata1 = new StatMetadata(generateMessageId(), SIZE_1);
        StatMetadata metadata2 = new StatMetadata(generateMessageId(), SIZE_2);
        Mono.from(testee().add(mailboxId, metadata1)).block();
        Mono.from(testee().add(mailboxId, metadata2)).block();

        Mono.from(testee().remove(mailboxId, metadata2)).block();

        assertThat(
            Flux.from(testee()
                .stat(mailboxId))
                .collectList()
                .block())
            .containsOnly(metadata1);
    }

    @Test
    default void statShouldNotReturnClearedData() {
        MailboxId mailboxId = generateMailboxId();
        StatMetadata metadata1 = new StatMetadata(generateMessageId(), SIZE_1);
        StatMetadata metadata2 = new StatMetadata(generateMessageId(), SIZE_2);
        Mono.from(testee().add(mailboxId, metadata1)).block();
        Mono.from(testee().add(mailboxId, metadata2)).block();

        Mono.from(testee().clear(mailboxId)).block();

        assertThat(
            Flux.from(testee()
                .stat(mailboxId))
                .collectList()
                .block())
            .isEmpty();
    }

    @Test
    default void addShouldUpsert() {
        MailboxId mailboxId = generateMailboxId();
        MessageId messageId = generateMessageId();
        StatMetadata metadata1 = new StatMetadata(messageId, SIZE_1);
        StatMetadata metadata2 = new StatMetadata(messageId, SIZE_2);
        Mono.from(testee().add(mailboxId, metadata1)).block();
        Mono.from(testee().add(mailboxId, metadata2)).block();

        assertThat(
            Flux.from(testee()
                .stat(mailboxId))
                .collectList()
                .block())
            .containsOnly(metadata2);
    }

    @Test
    default void clearShouldBeIdempotent() {
        assertThatCode(() -> Mono.from(testee().clear(generateMailboxId())).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void removeShouldBeIdempotent() {
        StatMetadata metadata1= new StatMetadata(generateMessageId(), SIZE_1);
        assertThatCode(() -> Mono.from(testee().remove(generateMailboxId(), metadata1)).block())
            .doesNotThrowAnyException();
    }
}
