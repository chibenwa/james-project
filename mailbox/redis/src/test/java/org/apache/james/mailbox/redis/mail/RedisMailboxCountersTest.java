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

package org.apache.james.mailbox.redis.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.mail.Flags;

import org.apache.james.backends.redis.DockerRedis;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.redis.RedisTestFixture;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.mail.MailboxCountersStore;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

class RedisMailboxCountersTest {
    private static final Username BOB = Username.of("bob");
    private static final String MESSAGE = "Subject: test\r\n\r\nbody\r\n";

    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    static class FakeLegacyMailboxCountersStore implements MailboxCountersStore {
        private final ConcurrentHashMap<MailboxId, MailboxCounters> values = new ConcurrentHashMap<>();

        @Override
        public Mono<MailboxCounters> retrieveMailboxCounters(Mailbox mailbox) {
            return Mono.justOrEmpty(values.get(mailbox.getMailboxId()));
        }

        @Override
        public Mono<Void> resetCounters(MailboxCounters counters) {
            return Mono.fromRunnable(() -> values.put(counters.getMailboxId(), counters));
        }

        @Override
        public Mono<Void> delete(MailboxId mailboxId) {
            return Mono.fromRunnable(() -> values.remove(mailboxId));
        }
    }

    private RedisMailboxCountersStore testee;
    private DockerRedis dockerRedis;
    private StoreMailboxManager mailboxManager;
    private MailboxSession session;
    private MailboxPath inboxPath;
    private MailboxPath otherPath;
    private MessageManager inbox;
    private Mailbox inboxEntity;
    private Mailbox otherEntity;

    @BeforeEach
    void setUp(DockerRedis dockerRedis) throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        this.dockerRedis = dockerRedis;
        testee = testee(Optional.empty());
        resources.getEventBus().register(new RedisMailboxCountersListener(testee));

        session = mailboxManager.createSystemSession(BOB);
        inboxPath = MailboxPath.inbox(BOB);
        otherPath = MailboxPath.forUser(BOB, "other");
        MailboxId inboxId = mailboxManager.createMailbox(inboxPath, session).get();
        MailboxId otherId = mailboxManager.createMailbox(otherPath, session).get();
        inbox = mailboxManager.getMailbox(inboxPath, session);
        inboxEntity = mailboxEntity(inboxId);
        otherEntity = mailboxEntity(otherId);
    }

    private RedisMailboxCountersStore testee(Optional<MailboxCountersStore> legacy) {
        return new RedisMailboxCountersStore(RedisTestFixture.counterCache(dockerRedis),
            mailboxManager::getMapperFactory,
            mailboxManager.getSessionProvider(),
            legacy);
    }

    private Mailbox mailboxEntity(MailboxId mailboxId) {
        return mailboxManager.getMapperFactory().getMailboxMapper(session).findMailboxById(mailboxId).block();
    }

    private void appendMessage(Flags flags) throws Exception {
        inbox.appendMessage(MessageManager.AppendCommand.builder()
            .withFlags(flags)
            .build(MESSAGE), session);
    }

    private MailboxCounters counters(Mailbox mailbox, long count, long unseen) {
        return MailboxCounters.builder()
            .mailboxId(mailbox.getMailboxId())
            .count(count)
            .unseen(unseen)
            .build();
    }

    private MailboxCounters retrieve(Mailbox mailbox) {
        return testee.retrieveMailboxCounters(mailbox).block();
    }

    @Test
    void retrieveShouldReturnZeroWhenEmptyMailbox() {
        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 0, 0));
    }

    @Test
    void retrieveShouldBeComputedFromMessages() throws Exception {
        appendMessage(new Flags());
        appendMessage(new Flags(Flags.Flag.SEEN));

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 2, 1));
    }

    @Test
    void appendShouldUpdateCachedCounters() throws Exception {
        appendMessage(new Flags());
        retrieve(inboxEntity);

        appendMessage(new Flags());
        appendMessage(new Flags(Flags.Flag.SEEN));

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 3, 2));
    }

    @Test
    void markingAsSeenShouldUpdateCachedCounters() throws Exception {
        appendMessage(new Flags());
        appendMessage(new Flags());
        retrieve(inboxEntity);

        inbox.setFlags(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.ADD, MessageRange.all(), session);

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 2, 0));
    }

    @Test
    void markingAsUnseenShouldUpdateCachedCounters() throws Exception {
        appendMessage(new Flags(Flags.Flag.SEEN));
        appendMessage(new Flags(Flags.Flag.SEEN));
        retrieve(inboxEntity);

        inbox.setFlags(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.REMOVE, MessageRange.all(), session);

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 2, 2));
    }

    @Test
    void unrelatedFlagsUpdatesShouldNotUpdateCachedCounters() throws Exception {
        appendMessage(new Flags());
        appendMessage(new Flags(Flags.Flag.SEEN));
        retrieve(inboxEntity);

        inbox.setFlags(new Flags(Flags.Flag.FLAGGED), MessageManager.FlagsUpdateMode.ADD, MessageRange.all(), session);

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 2, 1));
    }

    @Test
    void expungeShouldUpdateCachedCounters() throws Exception {
        appendMessage(new Flags(Flags.Flag.DELETED));
        appendMessage(new Flags(Flags.Flag.SEEN));
        appendMessage(new Flags());
        retrieve(inboxEntity);

        inbox.expunge(MessageRange.all(), session);

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 2, 1));
    }

    @Test
    void moveShouldUpdateCachedCounters() throws Exception {
        appendMessage(new Flags());
        appendMessage(new Flags(Flags.Flag.SEEN));
        retrieve(inboxEntity);
        retrieve(otherEntity);

        mailboxManager.moveMessages(MessageRange.all(), inboxPath, otherPath, session);

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 0, 0));
        assertThat(retrieve(otherEntity)).isEqualTo(counters(otherEntity, 2, 1));
    }

    @Test
    void mailboxDeletionShouldInvalidateCachedCounters() throws Exception {
        appendMessage(new Flags());
        retrieve(otherEntity);
        testee.resetCounters(counters(otherEntity, 36, 12)).block();

        mailboxManager.deleteMailbox(otherPath, session);

        assertThat(retrieve(otherEntity)).isEqualTo(counters(otherEntity, 0, 0));
    }

    @Test
    void resetCountersShouldReplaceCachedCounters() throws Exception {
        appendMessage(new Flags());
        retrieve(inboxEntity);

        testee.resetCounters(counters(inboxEntity, 36, 12)).block();

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 36, 12));
    }

    @Test
    void resetCountersShouldWarmUpTheCache() throws Exception {
        appendMessage(new Flags());

        testee.resetCounters(counters(inboxEntity, 36, 12)).block();

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 36, 12));
    }

    @Test
    void updatesShouldApplyAfterReset() throws Exception {
        testee.resetCounters(counters(inboxEntity, 36, 12)).block();

        appendMessage(new Flags());

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 37, 13));
    }

    @Test
    void deleteShouldInvalidateCachedCounters() throws Exception {
        appendMessage(new Flags());
        testee.resetCounters(counters(inboxEntity, 36, 12)).block();

        testee.delete(inboxEntity.getMailboxId()).block();

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 1, 1));
    }

    @Test
    void retrieveShouldRecomputeWhenRedisIsUnavailable() throws Exception {
        appendMessage(new Flags());
        retrieve(inboxEntity);
        testee.resetCounters(counters(inboxEntity, 36, 12)).block();

        RedisTestFixture.whileRedisIsPaused(dockerRedis, () ->
            assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 1, 1)));
    }

    @Test
    void writesShouldNotFailWhenRedisIsUnavailable() throws Exception {
        appendMessage(new Flags());
        retrieve(inboxEntity);

        RedisTestFixture.whileRedisIsPaused(dockerRedis, () ->
            assertThatCode(() -> {
                appendMessage(new Flags());
                inbox.setFlags(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.ADD, MessageRange.all(), session);
            }).doesNotThrowAnyException());
    }

    @Test
    void countersShouldBeCorrectAfterRedisOutage() throws Exception {
        appendMessage(new Flags());
        retrieve(inboxEntity);

        RedisTestFixture.whileRedisIsPaused(dockerRedis, Throwing.runnable(() -> {
            appendMessage(new Flags());
            appendMessage(new Flags(Flags.Flag.SEEN));
        }));

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 3, 2));
    }

    @Test
    void deleteShouldNotFailWhenRedisIsUnavailable() {
        RedisTestFixture.whileRedisIsPaused(dockerRedis, () ->
            assertThatCode(() -> testee.delete(inboxEntity.getMailboxId()).block())
                .doesNotThrowAnyException());
    }

    @Test
    void concurrentWritesShouldBeAccountedForWhenCached() throws Exception {
        retrieve(inboxEntity);

        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> appendMessage(threadNumber % 2 == 0 ? new Flags() : new Flags(Flags.Flag.SEEN)))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 100, 50));
    }

    @Test
    void concurrentFlagUpdatesShouldBeAccountedForWhenCached() throws Exception {
        for (int i = 0; i < 40; i++) {
            appendMessage(i % 2 == 0 ? new Flags() : new Flags(Flags.Flag.SEEN));
        }
        retrieve(inboxEntity);

        // Each thread updates its own messages: 4 threads mark 5 unseen messages as seen, 4 threads mark 5 seen messages as unseen
        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> {
                MessageUid uid = MessageUid.of(threadNumber * 10L + step * 2L + 1 + threadNumber % 2);
                MessageManager.FlagsUpdateMode mode = threadNumber % 2 == 0 ? MessageManager.FlagsUpdateMode.ADD : MessageManager.FlagsUpdateMode.REMOVE;
                inbox.setFlags(new Flags(Flags.Flag.SEEN), mode, MessageRange.one(uid), session);
            })
            .threadCount(4)
            .operationCount(5)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(retrieve(inboxEntity)).isEqualTo(counters(inboxEntity, 40, 20));
    }

    @Nested
    class MaintainLegacy {
        private FakeLegacyMailboxCountersStore legacy;
        private RedisMailboxCountersStore testee;

        @BeforeEach
        void setUp() {
            legacy = new FakeLegacyMailboxCountersStore();
            testee = testee(Optional.of(legacy));
        }

        @Test
        void retrieveShouldNotReadLegacyWhenRedisIsAvailable() throws Exception {
            appendMessage(new Flags());
            legacy.resetCounters(counters(inboxEntity, 36, 12)).block();

            assertThat(testee.retrieveMailboxCounters(inboxEntity).block()).isEqualTo(counters(inboxEntity, 1, 1));
        }

        @Test
        void retrieveShouldReadLegacyWhenRedisIsUnavailable() throws Exception {
            appendMessage(new Flags());
            legacy.resetCounters(counters(inboxEntity, 36, 12)).block();

            RedisTestFixture.whileRedisIsPaused(dockerRedis, () ->
                assertThat(testee.retrieveMailboxCounters(inboxEntity).block()).isEqualTo(counters(inboxEntity, 36, 12)));
        }

        @Test
        void retrieveShouldRecomputeWhenRedisIsUnavailableAndLegacyIsEmpty() throws Exception {
            appendMessage(new Flags());

            RedisTestFixture.whileRedisIsPaused(dockerRedis, () ->
                assertThat(testee.retrieveMailboxCounters(inboxEntity).block()).isEqualTo(counters(inboxEntity, 1, 1)));
        }

        @Test
        void resetCountersShouldUpdateLegacy() {
            testee.resetCounters(counters(inboxEntity, 36, 12)).block();

            assertThat(legacy.retrieveMailboxCounters(inboxEntity).block()).isEqualTo(counters(inboxEntity, 36, 12));
        }

        @Test
        void resetCountersShouldUpdateRedis() {
            testee.resetCounters(counters(inboxEntity, 36, 12)).block();

            assertThat(testee.retrieveMailboxCounters(inboxEntity).block()).isEqualTo(counters(inboxEntity, 36, 12));
        }
    }
}
