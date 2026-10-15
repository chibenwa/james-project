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

import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;

import java.util.Map;
import java.util.Optional;

import jakarta.inject.Provider;
import jakarta.mail.Flags;

import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.redis.RedisCounterCache;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxCountersStore;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

/**
 * Mailbox counters cached in Redis.
 *
 * On cache miss, counters are recomputed from the message metadata of the mailbox, which is the source of truth.
 * Cached values are then maintained by the {@link RedisMailboxCountersListener}, without ever failing.
 *
 * Optionally, a legacy store (maintained by the underlying mailbox implementation) is read when Redis is unavailable
 * and reset alongside Redis. Without legacy store, counters are recomputed when Redis is unavailable.
 */
public class RedisMailboxCountersStore implements MailboxCountersStore {
    private static final String KEY_PREFIX = "mailbox-counters:";
    private static final String COUNT = "count";
    private static final String UNSEEN = "unseen";
    private static final ImmutableList<String> FIELDS = ImmutableList.of(COUNT, UNSEEN);

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMailboxCountersStore.class);

    private final RedisCounterCache cache;
    // Lazy: the mapper factory itself depends on the MailboxCountersStore
    private final Provider<MailboxSessionMapperFactory> mapperFactory;
    private final SessionProvider sessionProvider;
    private final Optional<MailboxCountersStore> legacy;

    public RedisMailboxCountersStore(RedisCounterCache cache, Provider<MailboxSessionMapperFactory> mapperFactory,
                                     SessionProvider sessionProvider, Optional<MailboxCountersStore> legacy) {
        this.cache = cache;
        this.mapperFactory = mapperFactory;
        this.sessionProvider = sessionProvider;
        this.legacy = legacy;
    }

    @Override
    public Mono<MailboxCounters> retrieveMailboxCounters(Mailbox mailbox) {
        return cache.getOrCompute(key(mailbox.getMailboxId()), FIELDS, () -> recompute(mailbox), () -> fallback(mailbox))
            .map(values -> MailboxCounters.builder()
                .mailboxId(mailbox.getMailboxId())
                .count(values.get(COUNT))
                .unseen(values.get(UNSEEN))
                .build());
    }

    /**
     * Resetting counters is idempotent: failures are propagated so that the caller can retry.
     */
    @Override
    public Mono<Void> resetCounters(MailboxCounters counters) {
        return Mono.when(
            cache.replace(key(counters.getMailboxId()), ImmutableMap.of(
                COUNT, counters.getCount(),
                UNSEEN, counters.getUnseen())),
            legacy.map(legacy -> legacy.resetCounters(counters))
                .orElse(Mono.empty()));
    }

    /**
     * Invalidates the cached counters. Never fails.
     *
     * Legacy counters are deleted by the underlying mailbox implementation.
     */
    @Override
    public Mono<Void> delete(MailboxId mailboxId) {
        return cache.invalidate(key(mailboxId))
            .onErrorResume(e -> {
                LOGGER.error("Failed invalidating counters of {}: they might be stale until they expire", mailboxId.serialize(), e);
                return Mono.empty();
            });
    }

    public Mono<Void> applyDelta(MailboxId mailboxId, long countDelta, long unseenDelta) {
        if (countDelta == 0 && unseenDelta == 0) {
            return Mono.empty();
        }
        return cache.incrementIfPresent(key(mailboxId), ImmutableMap.of(
            COUNT, countDelta,
            UNSEEN, unseenDelta));
    }

    private Mono<Map<String, Long>> fallback(Mailbox mailbox) {
        return legacy.map(legacy -> legacy.retrieveMailboxCounters(mailbox)
                .map(counters -> (Map<String, Long>) ImmutableMap.of(
                    COUNT, counters.getCount(),
                    UNSEEN, counters.getUnseen()))
                .switchIfEmpty(Mono.defer(() -> recompute(mailbox)))
                .onErrorResume(e -> {
                    LOGGER.warn("Failed reading legacy counters of {}, recomputing them", mailbox.getMailboxId().serialize(), e);
                    return recompute(mailbox);
                }))
            .orElseGet(() -> recompute(mailbox));
    }

    private Mono<Map<String, Long>> recompute(Mailbox mailbox) {
        return Mono.fromCallable(() -> mapperFactory.get().getMessageMapper(sessionProvider.createSystemSession(mailbox.getUser())))
            .flatMap(messageMapper -> messageMapper.findInMailboxReactive(mailbox, MessageRange.all(), MessageMapper.FetchType.METADATA, UNLIMITED)
                .reduce(new long[] {0, 0}, (counters, message) -> {
                    counters[0]++;
                    if (!message.createFlags().contains(Flags.Flag.SEEN)) {
                        counters[1]++;
                    }
                    return counters;
                }))
            .map(counters -> ImmutableMap.of(
                COUNT, counters[0],
                UNSEEN, counters[1]));
    }

    private String key(MailboxId mailboxId) {
        return KEY_PREFIX + mailboxId.serialize();
    }
}
