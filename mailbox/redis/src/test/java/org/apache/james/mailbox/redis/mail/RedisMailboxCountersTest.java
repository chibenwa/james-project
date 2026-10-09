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

import jakarta.mail.Flags;

import org.apache.james.backends.redis.DockerRedis;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.redis.RedisTestFixture;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RedisMailboxCountersTest {
    private static final Username BOB = Username.of("bob");
    private static final String MESSAGE = "Subject: test\r\n\r\nbody\r\n";

    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    private RedisMailboxCountersStore testee;
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
        testee = new RedisMailboxCountersStore(RedisTestFixture.counterCache(dockerRedis),
            mailboxManager::getMapperFactory,
            mailboxManager.getSessionProvider());
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
}
