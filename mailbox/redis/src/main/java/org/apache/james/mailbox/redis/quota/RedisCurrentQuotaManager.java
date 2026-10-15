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

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.redis.RedisCounterCache;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

/**
 * Current quotas cached in Redis.
 *
 * On cache miss, current quotas are recomputed from the messages of the quota root, which are the source of truth.
 * Increments and decrements, applied by the {@link org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater},
 * only modify cached values and never fail. Setting current quotas, eg upon quota recomputation, replaces the cached
 * value.
 *
 * Optionally, a legacy current quota manager is maintained alongside Redis (best effort for increments). It is then
 * read when Redis is unavailable, and allows rolling back to it. Without legacy, current quotas are recomputed when
 * Redis is unavailable.
 */
public class RedisCurrentQuotaManager implements CurrentQuotaManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCurrentQuotaManager.class);
    private static final String KEY_PREFIX = "current-quota:";
    private static final String COUNT = "count";
    private static final String SIZE = "size";
    private static final ImmutableList<String> FIELDS = ImmutableList.of(COUNT, SIZE);
    private static final int MAILBOX_CONCURRENCY = 4;

    private final RedisCounterCache cache;
    private final CurrentQuotaCalculator currentQuotaCalculator;
    private final QuotaRootResolver quotaRootResolver;
    private final SessionProvider sessionProvider;
    private final Optional<CurrentQuotaManager> legacy;

    public RedisCurrentQuotaManager(RedisCounterCache cache, CurrentQuotaCalculator currentQuotaCalculator,
                                    QuotaRootResolver quotaRootResolver, SessionProvider sessionProvider,
                                    Optional<CurrentQuotaManager> legacy) {
        this.cache = cache;
        this.currentQuotaCalculator = currentQuotaCalculator;
        this.quotaRootResolver = quotaRootResolver;
        this.sessionProvider = sessionProvider;
        this.legacy = legacy;
    }

    @Override
    public Mono<QuotaCountUsage> getCurrentMessageCount(QuotaRoot quotaRoot) {
        return getCurrentQuotas(quotaRoot).map(CurrentQuotas::count);
    }

    @Override
    public Mono<QuotaSizeUsage> getCurrentStorage(QuotaRoot quotaRoot) {
        return getCurrentQuotas(quotaRoot).map(CurrentQuotas::size);
    }

    @Override
    public Mono<CurrentQuotas> getCurrentQuotas(QuotaRoot quotaRoot) {
        return cache.getOrCompute(key(quotaRoot), FIELDS, () -> recompute(quotaRoot), () -> fallback(quotaRoot))
            .map(values -> new CurrentQuotas(
                QuotaCountUsage.count(values.get(COUNT)),
                QuotaSizeUsage.size(values.get(SIZE))));
    }

    @Override
    public Mono<Void> increase(QuotaOperation quotaOperation) {
        return cache.incrementIfPresent(key(quotaOperation.quotaRoot()), ImmutableMap.of(
                COUNT, quotaOperation.count().asLong(),
                SIZE, quotaOperation.size().asLong()))
            .then(onLegacy(legacy -> legacy.increase(quotaOperation), quotaOperation));
    }

    @Override
    public Mono<Void> decrease(QuotaOperation quotaOperation) {
        return cache.incrementIfPresent(key(quotaOperation.quotaRoot()), ImmutableMap.of(
                COUNT, -quotaOperation.count().asLong(),
                SIZE, -quotaOperation.size().asLong()))
            .then(onLegacy(legacy -> legacy.decrease(quotaOperation), quotaOperation));
    }

    /**
     * Setting values is idempotent: failures are propagated so that the caller can retry.
     */
    @Override
    public Mono<Void> setCurrentQuotas(QuotaOperation quotaOperation) {
        return Mono.when(
            cache.replace(key(quotaOperation.quotaRoot()), ImmutableMap.of(
                COUNT, quotaOperation.count().asLong(),
                SIZE, quotaOperation.size().asLong())),
            legacy.map(legacy -> Mono.from(legacy.setCurrentQuotas(quotaOperation)))
                .orElse(Mono.empty()));
    }

    private Mono<Void> onLegacy(Function<CurrentQuotaManager, Publisher<Void>> operation, QuotaOperation quotaOperation) {
        return legacy.map(legacy -> Mono.from(operation.apply(legacy))
                .onErrorResume(e -> {
                    LOGGER.warn("Failed updating legacy current quotas for {}", quotaOperation.quotaRoot().getValue(), e);
                    return Mono.empty();
                }))
            .orElse(Mono.empty());
    }

    private Mono<Map<String, Long>> fallback(QuotaRoot quotaRoot) {
        return legacy.map(legacy -> Mono.from(legacy.getCurrentQuotas(quotaRoot))
                .map(this::asMap)
                .onErrorResume(e -> {
                    LOGGER.warn("Failed reading legacy current quotas for {}, recomputing them", quotaRoot.getValue(), e);
                    return recompute(quotaRoot);
                }))
            .orElseGet(() -> recompute(quotaRoot));
    }

    private Mono<Map<String, Long>> recompute(QuotaRoot quotaRoot) {
        return Mono.fromCallable(() -> sessionProvider.createSystemSession(quotaRootResolver.associatedUsername(quotaRoot)))
            .flatMap(session -> currentQuotaCalculator.recalculateCurrentQuotas(quotaRoot, session, MAILBOX_CONCURRENCY))
            .map(this::asMap);
    }

    private Map<String, Long> asMap(CurrentQuotas currentQuotas) {
        return ImmutableMap.of(
            COUNT, currentQuotas.count().asLong(),
            SIZE, currentQuotas.size().asLong());
    }

    private String key(QuotaRoot quotaRoot) {
        return KEY_PREFIX + quotaRoot.getValue();
    }
}
