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

import static org.apache.james.WithRedisMailboxCachesTest.AWAIT;
import static org.apache.james.WithRedisMailboxCachesTest.appendMessage;
import static org.apache.james.WithRedisMailboxCachesTest.whileRedisIsPaused;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Flags;

import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;

class WithRedisMailboxCachesMaintainingLegacyTest {
    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Username ALICE = Username.of("alice@domain.tld");

    static RedisExtension redisExtension = new RedisExtension();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = WithRedisMailboxCachesTest.baseExtension(redisExtension, true).build();

    @Test
    void legacyStructuresShouldBeMaintained(GuiceJamesServer server) throws Exception {
        RedisMailboxCachesProbe probe = server.getProbe(RedisMailboxCachesProbe.class);
        MailboxPath inbox = MailboxPath.inbox(BOB);
        server.getProbe(MailboxProbeImpl.class).createMailbox(inbox);

        appendMessage(server, inbox, new Flags());
        appendMessage(server, inbox, new Flags(Flags.Flag.SEEN));

        AWAIT.untilAsserted(() -> {
            assertThat(probe.cassandraCurrentQuotas(BOB).count()).isEqualTo(QuotaCountUsage.count(2));
            assertThat(probe.cassandraMailboxCounters(inbox).getCount()).isEqualTo(2);
            assertThat(probe.cassandraMailboxCounters(inbox).getUnseen()).isEqualTo(1);
        });
    }

    @Test
    void legacyStructuresShouldBeReadWhileRedisIsUnavailable(GuiceJamesServer server) throws Exception {
        RedisMailboxCachesProbe probe = server.getProbe(RedisMailboxCachesProbe.class);
        MailboxPath inbox = MailboxPath.inbox(ALICE);
        server.getProbe(MailboxProbeImpl.class).createMailbox(inbox);
        appendMessage(server, inbox, new Flags());
        AWAIT.untilAsserted(() -> assertThat(probe.cassandraCurrentQuotas(ALICE).count()).isEqualTo(QuotaCountUsage.count(1)));
        // Drift legacy counters so that reading them is observable
        probe.addToCassandraMailboxCounters(MailboxCounters.builder()
            .mailboxId(probe.mailbox(inbox).getMailboxId())
            .count(10)
            .unseen(10)
            .build());

        whileRedisIsPaused(redisExtension, Throwing.runnable(() -> {
            appendMessage(server, inbox, new Flags());

            AWAIT.untilAsserted(() -> assertThat(probe.currentMessageCount(ALICE)).isEqualTo(QuotaCountUsage.count(2)));
            MailboxCounters counters = probe.mailboxCounters(inbox);
            assertThat(counters.getCount()).isEqualTo(12);
            assertThat(counters.getUnseen()).isEqualTo(12);
        }));

        // Legacy current quotas kept being maintained during the outage
        assertThat(probe.cassandraCurrentQuotas(ALICE).count()).isEqualTo(QuotaCountUsage.count(2));
    }
}
