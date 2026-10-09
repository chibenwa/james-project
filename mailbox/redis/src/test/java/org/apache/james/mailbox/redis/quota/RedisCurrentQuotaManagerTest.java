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

package org.apache.james.mailbox.redis.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.backends.redis.DockerRedis;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.redis.RedisTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

class RedisCurrentQuotaManagerTest {
    private static final Username BOB = Username.of("bob");
    private static final String MESSAGE = "Subject: test\r\n\r\nbody\r\n";
    private static final long MESSAGE_SIZE = MESSAGE.getBytes(StandardCharsets.UTF_8).length;

    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    private RedisCurrentQuotaManager testee;
    private InMemoryIntegrationResources resources;
    private MailboxSession session;
    private MessageManager inbox;
    private QuotaRoot quotaRoot;

    @BeforeEach
    void setUp(DockerRedis dockerRedis) throws Exception {
        resources = InMemoryIntegrationResources.defaultResources();
        testee = new RedisCurrentQuotaManager(RedisTestFixture.counterCache(dockerRedis),
            resources.getCurrentQuotaCalculator(),
            resources.getQuotaRootResolver(),
            resources.getMailboxManager().getSessionProvider());

        session = resources.getMailboxManager().createSystemSession(BOB);
        MailboxPath inboxPath = MailboxPath.inbox(BOB);
        resources.getMailboxManager().createMailbox(inboxPath, session);
        inbox = resources.getMailboxManager().getMailbox(inboxPath, session);
        quotaRoot = resources.getDefaultUserQuotaRootResolver().forUser(BOB);
    }

    private void appendMessage() throws Exception {
        inbox.appendMessage(MessageManager.AppendCommand.builder().build(MESSAGE), session);
    }

    private CurrentQuotas currentQuotas(long count, long size) {
        return new CurrentQuotas(QuotaCountUsage.count(count), QuotaSizeUsage.size(size));
    }

    @Test
    void getCurrentQuotasShouldReturnZeroWhenNoMessages() {
        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(CurrentQuotas.emptyQuotas());
    }

    @Test
    void getCurrentQuotasShouldBeComputedFromMessages() throws Exception {
        appendMessage();
        appendMessage();

        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(currentQuotas(2, 2 * MESSAGE_SIZE));
    }

    @Test
    void getCurrentMessageCountShouldBeComputedFromMessages() throws Exception {
        appendMessage();
        appendMessage();

        assertThat(testee.getCurrentMessageCount(quotaRoot).block())
            .isEqualTo(QuotaCountUsage.count(2));
    }

    @Test
    void getCurrentStorageShouldBeComputedFromMessages() throws Exception {
        appendMessage();
        appendMessage();

        assertThat(testee.getCurrentStorage(quotaRoot).block())
            .isEqualTo(QuotaSizeUsage.size(2 * MESSAGE_SIZE));
    }

    @Test
    void getCurrentQuotasShouldReturnCachedValue() throws Exception {
        appendMessage();
        testee.getCurrentQuotas(quotaRoot).block();

        appendMessage();

        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(currentQuotas(1, MESSAGE_SIZE));
    }

    @Test
    void increaseShouldUpdateCachedValue() throws Exception {
        appendMessage();
        testee.getCurrentQuotas(quotaRoot).block();

        Mono.from(testee.increase(new QuotaOperation(quotaRoot, QuotaCountUsage.count(2), QuotaSizeUsage.size(100)))).block();

        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(currentQuotas(3, MESSAGE_SIZE + 100));
    }

    @Test
    void increaseShouldBeIgnoredWhenNotCached() throws Exception {
        appendMessage();

        Mono.from(testee.increase(new QuotaOperation(quotaRoot, QuotaCountUsage.count(2), QuotaSizeUsage.size(100)))).block();

        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(currentQuotas(1, MESSAGE_SIZE));
    }

    @Test
    void decreaseShouldUpdateCachedValue() throws Exception {
        appendMessage();
        appendMessage();
        testee.getCurrentQuotas(quotaRoot).block();

        Mono.from(testee.decrease(new QuotaOperation(quotaRoot, QuotaCountUsage.count(1), QuotaSizeUsage.size(MESSAGE_SIZE)))).block();

        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(currentQuotas(1, MESSAGE_SIZE));
    }

    @Test
    void decreaseShouldTolerateNegativeValues() {
        testee.getCurrentQuotas(quotaRoot).block();

        Mono.from(testee.decrease(new QuotaOperation(quotaRoot, QuotaCountUsage.count(1), QuotaSizeUsage.size(10)))).block();

        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(currentQuotas(-1, -10));
    }

    @Test
    void decreaseShouldBeIgnoredWhenNotCached() throws Exception {
        appendMessage();

        Mono.from(testee.decrease(new QuotaOperation(quotaRoot, QuotaCountUsage.count(1), QuotaSizeUsage.size(10)))).block();

        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(currentQuotas(1, MESSAGE_SIZE));
    }

    @Test
    void setCurrentQuotasShouldReplaceCachedValue() throws Exception {
        appendMessage();
        testee.getCurrentQuotas(quotaRoot).block();

        Mono.from(testee.setCurrentQuotas(new QuotaOperation(quotaRoot, QuotaCountUsage.count(10), QuotaSizeUsage.size(100)))).block();

        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(currentQuotas(10, 100));
    }

    @Test
    void setCurrentQuotasShouldWarmUpTheCache() throws Exception {
        appendMessage();

        Mono.from(testee.setCurrentQuotas(new QuotaOperation(quotaRoot, QuotaCountUsage.count(10), QuotaSizeUsage.size(100)))).block();

        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(currentQuotas(10, 100));
    }

    @Test
    void setCurrentQuotasShouldBeIdempotent() {
        QuotaOperation quotaOperation = new QuotaOperation(quotaRoot, QuotaCountUsage.count(10), QuotaSizeUsage.size(100));

        Mono.from(testee.setCurrentQuotas(quotaOperation)).block();
        Mono.from(testee.setCurrentQuotas(quotaOperation)).block();

        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(currentQuotas(10, 100));
    }

    @Test
    void increaseShouldApplyAfterSetCurrentQuotas() {
        Mono.from(testee.setCurrentQuotas(new QuotaOperation(quotaRoot, QuotaCountUsage.count(10), QuotaSizeUsage.size(100)))).block();

        Mono.from(testee.increase(new QuotaOperation(quotaRoot, QuotaCountUsage.count(1), QuotaSizeUsage.size(10)))).block();

        assertThat(testee.getCurrentQuotas(quotaRoot).block())
            .isEqualTo(currentQuotas(11, 110));
    }
}
