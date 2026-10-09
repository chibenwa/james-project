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

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.redis.mail.RedisMailboxCountersStore;
import org.apache.james.mailbox.redis.quota.RedisCurrentQuotaManager;
import org.apache.james.mailbox.store.mail.MailboxCountersStore;
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

import com.google.inject.multibindings.Multibinder;

import reactor.core.publisher.Mono;

class WithRedisMailboxCachesTest {
    public static class RedisMailboxCachesProbe implements GuiceProbe {
        private final CurrentQuotaManager currentQuotaManager;
        private final MailboxCountersStore mailboxCountersStore;
        private final UserQuotaRootResolver quotaRootResolver;
        private final MailboxManager mailboxManager;

        @Inject
        public RedisMailboxCachesProbe(CurrentQuotaManager currentQuotaManager, MailboxCountersStore mailboxCountersStore,
                                       UserQuotaRootResolver quotaRootResolver, MailboxManager mailboxManager) {
            this.currentQuotaManager = currentQuotaManager;
            this.mailboxCountersStore = mailboxCountersStore;
            this.quotaRootResolver = quotaRootResolver;
            this.mailboxManager = mailboxManager;
        }

        QuotaCountUsage currentMessageCount(Username username) {
            return Mono.from(currentQuotaManager.getCurrentMessageCount(quotaRootResolver.forUser(username))).block();
        }

        MailboxCounters mailboxCounters(MailboxPath mailboxPath) throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(mailboxPath.getUser());
            return mailboxManager.getMailbox(mailboxPath, session).getMailboxCounters(session);
        }
    }

    private static final Username BOB = Username.of("bob@domain.tld");
    private static final MailboxPath INBOX = MailboxPath.inbox(BOB);
    private static final ConditionFactory AWAIT = Awaitility.await()
        .pollInterval(ONE_HUNDRED_MILLISECONDS)
        .atMost(Duration.ofSeconds(30));

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
            CassandraRabbitMQJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .blobStore(BlobStoreConfiguration.builder()
                    .s3()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig())
                .searchConfiguration(SearchConfiguration.scanning())
                .redisMailboxConfiguration(new RedisMailboxConfiguration(true, true))
                .build())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(RedisMailboxCachesProbe.class)))
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new RedisExtension())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Test
    void redisImplementationsShouldBeBound(GuiceJamesServer server) {
        RedisMailboxCachesProbe probe = server.getProbe(RedisMailboxCachesProbe.class);

        assertThat(probe.currentQuotaManager).isInstanceOf(RedisCurrentQuotaManager.class);
        assertThat(probe.mailboxCountersStore).isInstanceOf(RedisMailboxCountersStore.class);
    }

    @Test
    void cachesShouldBeMaintainedUponWrites(GuiceJamesServer server) throws Exception {
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        RedisMailboxCachesProbe probe = server.getProbe(RedisMailboxCachesProbe.class);
        mailboxProbe.createMailbox(INBOX);

        mailboxProbe.appendMessage(BOB.asString(), INBOX, MessageManager.AppendCommand.builder()
            .build("Subject: first\r\n\r\nbody\r\n"));
        // Quota check upon append populates the current quota cache, updated asynchronously by the quota listener
        AWAIT.untilAsserted(() -> {
            assertThat(probe.currentMessageCount(BOB)).isEqualTo(QuotaCountUsage.count(1));
            assertThat(probe.mailboxCounters(INBOX).getCount()).isEqualTo(1);
        });

        mailboxProbe.appendMessage(BOB.asString(), INBOX, MessageManager.AppendCommand.builder()
            .withFlags(new Flags(Flags.Flag.SEEN))
            .build("Subject: second\r\n\r\nbody\r\n"));

        AWAIT.untilAsserted(() -> {
            assertThat(probe.currentMessageCount(BOB)).isEqualTo(QuotaCountUsage.count(2));
            MailboxCounters counters = probe.mailboxCounters(INBOX);
            assertThat(counters.getCount()).isEqualTo(2);
            assertThat(counters.getUnseen()).isEqualTo(1);
        });
    }
}
