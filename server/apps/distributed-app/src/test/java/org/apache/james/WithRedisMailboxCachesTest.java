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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.time.Duration;

import jakarta.mail.Flags;

import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.redis.RedisComponentConfiguration;
import org.apache.james.mailbox.redis.mail.RedisMailboxCountersStore;
import org.apache.james.mailbox.redis.quota.RedisCurrentQuotaManager;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.mailbox.redis.RedisMailboxConfiguration;
import org.apache.james.utils.GuiceProbe;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.google.inject.multibindings.Multibinder;

class WithRedisMailboxCachesTest {
    static final Duration REDIS_TIMEOUT = Duration.ofSeconds(1);
    static final ConditionFactory AWAIT = Awaitility.await()
        .pollInterval(ONE_HUNDRED_MILLISECONDS)
        .atMost(Duration.ofSeconds(30));

    static JamesServerBuilder<CassandraRabbitMQJamesConfiguration> baseExtension(RedisExtension redisExtension, boolean maintainLegacy) {
        RedisComponentConfiguration componentConfiguration = new RedisComponentConfiguration(true, REDIS_TIMEOUT, maintainLegacy);

        return new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
            CassandraRabbitMQJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .blobStore(BlobStoreConfiguration.builder()
                    .s3()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig())
                .searchConfiguration(SearchConfiguration.scanning())
                .redisMailboxConfiguration(new RedisMailboxConfiguration(componentConfiguration, componentConfiguration))
                .build())
            .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
                .overrideWith(new TestJMAPServerModule())
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(RedisMailboxCachesProbe.class)))
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .extension(new AwsS3BlobStoreExtension())
            .extension(redisExtension)
            .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS);
    }

    static void appendMessage(GuiceJamesServer server, MailboxPath mailboxPath, Flags flags) throws Exception {
        server.getProbe(MailboxProbeImpl.class).appendMessage(mailboxPath.getUser().asString(), mailboxPath, MessageManager.AppendCommand.builder()
            .withFlags(flags)
            .build("Subject: test\r\n\r\nbody\r\n"));
    }

    static void whileRedisIsPaused(RedisExtension redisExtension, Runnable operation) {
        redisExtension.dockerRedis().pause();
        try {
            operation.run();
        } finally {
            redisExtension.dockerRedis().unPause();
        }
    }

    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Username ALICE = Username.of("alice@domain.tld");
    private static final Username CEDRIC = Username.of("cedric@domain.tld");

    static RedisExtension redisExtension = new RedisExtension();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = baseExtension(redisExtension, false).build();

    @Test
    void redisImplementationsShouldBeBound(GuiceJamesServer server) {
        RedisMailboxCachesProbe probe = server.getProbe(RedisMailboxCachesProbe.class);

        assertThat(probe.currentQuotaManager).isInstanceOf(RedisCurrentQuotaManager.class);
        assertThat(probe.mailboxCountersStore).isInstanceOf(RedisMailboxCountersStore.class);
    }

    @Test
    void cachesShouldBeMaintainedUponWrites(GuiceJamesServer server) throws Exception {
        RedisMailboxCachesProbe probe = server.getProbe(RedisMailboxCachesProbe.class);
        MailboxPath inbox = MailboxPath.inbox(BOB);
        server.getProbe(MailboxProbeImpl.class).createMailbox(inbox);

        appendMessage(server, inbox, new Flags());
        // Quota check upon append populates the current quota cache, updated asynchronously by the quota listener
        AWAIT.untilAsserted(() -> {
            assertThat(probe.currentMessageCount(BOB)).isEqualTo(QuotaCountUsage.count(1));
            assertThat(probe.mailboxCounters(inbox).getCount()).isEqualTo(1);
        });

        appendMessage(server, inbox, new Flags(Flags.Flag.SEEN));

        AWAIT.untilAsserted(() -> {
            assertThat(probe.currentMessageCount(BOB)).isEqualTo(QuotaCountUsage.count(2));
            MailboxCounters counters = probe.mailboxCounters(inbox);
            assertThat(counters.getCount()).isEqualTo(2);
            assertThat(counters.getUnseen()).isEqualTo(1);
        });
    }

    @Test
    void legacyStructuresShouldNotBeMaintained(GuiceJamesServer server) throws Exception {
        RedisMailboxCachesProbe probe = server.getProbe(RedisMailboxCachesProbe.class);
        MailboxPath inbox = MailboxPath.inbox(ALICE);
        server.getProbe(MailboxProbeImpl.class).createMailbox(inbox);

        appendMessage(server, inbox, new Flags());
        AWAIT.untilAsserted(() -> assertThat(probe.currentMessageCount(ALICE)).isEqualTo(QuotaCountUsage.count(1)));

        assertThat(probe.cassandraCurrentQuotas(ALICE)).isEqualTo(CurrentQuotas.emptyQuotas());
        assertThat(probe.cassandraMailboxCounters(inbox).getCount()).isZero();
    }

    @Test
    void readsAndWritesShouldWorkWhileRedisIsUnavailable(GuiceJamesServer server) throws Exception {
        RedisMailboxCachesProbe probe = server.getProbe(RedisMailboxCachesProbe.class);
        MailboxPath inbox = MailboxPath.inbox(CEDRIC);
        server.getProbe(MailboxProbeImpl.class).createMailbox(inbox);
        appendMessage(server, inbox, new Flags());

        whileRedisIsPaused(redisExtension, Throwing.runnable(() -> {
            appendMessage(server, inbox, new Flags(Flags.Flag.SEEN));

            // Recomputed from the source of truth
            assertThat(probe.currentMessageCount(CEDRIC)).isEqualTo(QuotaCountUsage.count(2));
            MailboxCounters counters = probe.mailboxCounters(inbox);
            assertThat(counters.getCount()).isEqualTo(2);
            assertThat(counters.getUnseen()).isEqualTo(1);
        }));
    }
}
