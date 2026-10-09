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

package org.apache.james.mailbox.redis;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.backends.redis.RedisReactiveCommandsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Longs;

import io.lettuce.core.KeyValue;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import reactor.core.publisher.Mono;

/**
 * Caches counters in Redis hashes. The source of truth lives elsewhere: on cache miss the counters are recomputed
 * from it, then stored with a TTL.
 *
 * Increments are only applied to entries already present in the cache: an absent entry will be recomputed upon
 * next read.
 *
 * Each entry carries a generation, bumped by every write. A recomputed value is only stored if the generation did
 * not change since the cache miss: this prevents concurrent increments being lost while the recomputation runs.
 *
 * Partially stored entries (missing or unreadable counters) are treated as cache misses.
 */
public class RedisCounterCache {
    sealed interface CacheRead {
        record Hit(Map<String, Long> values) implements CacheRead {

        }

        record Miss(String generation) implements CacheRead {

        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCounterCache.class);
    private static final String GENERATION_FIELD = "gen";
    private static final String NO_GENERATION = "0";

    // KEYS[1] = key, ARGV[1] = ttl in seconds, ARGV[2..] = field, delta pairs
    private static final String INCREMENT_IF_PRESENT_SCRIPT = """
        local exists = redis.call('EXISTS', KEYS[1])
        redis.call('HINCRBY', KEYS[1], 'gen', 1)
        if exists == 0 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            return 0
        end
        for i = 2, #ARGV, 2 do
            if redis.call('HEXISTS', KEYS[1], ARGV[i]) == 0 then
                return 0
            end
        end
        for i = 2, #ARGV, 2 do
            redis.call('HINCRBY', KEYS[1], ARGV[i], ARGV[i + 1])
        end
        return 1
        """;

    // KEYS[1] = key, ARGV[1] = expected generation, ARGV[2] = ttl in seconds, ARGV[3..] = field, value pairs
    private static final String STORE_IF_GENERATION_MATCHES_SCRIPT = """
        local generation = redis.call('HGET', KEYS[1], 'gen') or '0'
        if generation ~= ARGV[1] then
            return 0
        end
        for i = 3, #ARGV, 2 do
            redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
        end
        redis.call('EXPIRE', KEYS[1], ARGV[2])
        return 1
        """;

    // KEYS[1] = key, ARGV[1] = ttl in seconds, ARGV[2..] = field, value pairs
    private static final String REPLACE_SCRIPT = """
        local generation = redis.call('HINCRBY', KEYS[1], 'gen', 1)
        redis.call('DEL', KEYS[1])
        redis.call('HSET', KEYS[1], 'gen', generation)
        for i = 2, #ARGV, 2 do
            redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
        end
        redis.call('EXPIRE', KEYS[1], ARGV[1])
        return 1
        """;

    // KEYS[1] = key, ARGV[1] = ttl in seconds
    private static final String INVALIDATE_SCRIPT = """
        local generation = redis.call('HINCRBY', KEYS[1], 'gen', 1)
        redis.call('DEL', KEYS[1])
        redis.call('HSET', KEYS[1], 'gen', generation)
        redis.call('EXPIRE', KEYS[1], ARGV[1])
        return 1
        """;

    private final RedisClusterReactiveCommands<String, String> commands;
    private final RedisMailboxCacheConfiguration configuration;
    private final ConcurrentHashMap<String, Mono<Map<String, Long>>> ongoingComputations;

    @Inject
    public RedisCounterCache(RedisReactiveCommandsFactory commandsFactory, RedisMailboxCacheConfiguration configuration) {
        this(commandsFactory.<RedisClusterReactiveCommands<String, String>>create(commands -> commands, commands -> commands), configuration);
    }

    @VisibleForTesting
    public RedisCounterCache(RedisClusterReactiveCommands<String, String> commands, RedisMailboxCacheConfiguration configuration) {
        this.commands = commands;
        this.configuration = configuration;
        this.ongoingComputations = new ConcurrentHashMap<>();
    }

    /**
     * Reads the counters stored under this key. On cache miss, they are computed via the supplied computation, then
     * stored. Concurrent computations for the same key are shared.
     */
    public Mono<Map<String, Long>> getOrCompute(String key, Collection<String> fields, Supplier<Mono<Map<String, Long>>> computation) {
        return read(key, fields)
            .flatMap(cacheRead -> switch (cacheRead) {
                case CacheRead.Hit hit -> Mono.just(hit.values());
                case CacheRead.Miss miss -> computeAndStore(key, miss.generation(), computation);
            });
    }

    /**
     * Adds the deltas to the stored counters. No-op if the counters are not stored.
     *
     * If the outcome of the increment is unknown (Redis error), the entry is invalidated so that it gets recomputed
     * upon next read, instead of risking over or under counting upon retries.
     */
    public Mono<Void> incrementIfPresent(String key, Map<String, Long> deltas) {
        return eval(INCREMENT_IF_PRESENT_SCRIPT, key, Stream.concat(
                Stream.of(ttlSeconds()),
                asArguments(deltas)))
            .then()
            .onErrorResume(e -> {
                LOGGER.warn("Failed incrementing {}, invalidating it", key, e);
                return invalidate(key)
                    .onErrorMap(invalidationError -> {
                        e.addSuppressed(invalidationError);
                        return e;
                    });
            });
    }

    /**
     * Atomically discards the stored entry then stores these values.
     */
    public Mono<Void> replace(String key, Map<String, Long> values) {
        return eval(REPLACE_SCRIPT, key, Stream.concat(
                Stream.of(ttlSeconds()),
                asArguments(values)))
            .then();
    }

    /**
     * Discards the stored entry: it will be recomputed upon next read.
     */
    public Mono<Void> invalidate(String key) {
        return eval(INVALIDATE_SCRIPT, key, Stream.of(ttlSeconds()))
            .then();
    }

    @VisibleForTesting
    Mono<CacheRead> read(String key, Collection<String> fields) {
        return commands.hgetall(key)
            .collectMap(KeyValue::getKey, KeyValue::getValue)
            .map(storedValues -> asCacheRead(storedValues, fields));
    }

    @VisibleForTesting
    Mono<Boolean> storeIfGenerationMatches(String key, String generation, Map<String, Long> values) {
        return eval(STORE_IF_GENERATION_MATCHES_SCRIPT, key, Stream.concat(
                Stream.of(generation, ttlSeconds()),
                asArguments(values)))
            .map(result -> result == 1L);
    }

    private CacheRead asCacheRead(Map<String, String> storedValues, Collection<String> fields) {
        ImmutableMap.Builder<String, Long> values = ImmutableMap.builder();
        for (String field : fields) {
            Optional<Long> value = Optional.ofNullable(storedValues.get(field)).map(Longs::tryParse);
            if (value.isEmpty()) {
                return new CacheRead.Miss(storedValues.getOrDefault(GENERATION_FIELD, NO_GENERATION));
            }
            values.put(field, value.get());
        }
        return new CacheRead.Hit(values.build());
    }

    private Mono<Map<String, Long>> computeAndStore(String key, String generation, Supplier<Mono<Map<String, Long>>> computation) {
        AtomicReference<Mono<Map<String, Long>>> sharedComputation = new AtomicReference<>();
        return ongoingComputations.computeIfAbsent(key, any -> {
            Mono<Map<String, Long>> result = Mono.defer(computation)
                .flatMap(values -> storeIfGenerationMatches(key, generation, values)
                    .doOnNext(stored -> {
                        if (!stored) {
                            LOGGER.debug("{} was concurrently updated, skip caching the recomputed value", key);
                        }
                    })
                    .thenReturn(values))
                .doFinally(signal -> ongoingComputations.remove(key, sharedComputation.get()))
                .cache();
            sharedComputation.set(result);
            return result;
        });
    }

    private Mono<Long> eval(String script, String key, Stream<String> arguments) {
        return commands.<Long>eval(script, ScriptOutputType.INTEGER, new String[] {key}, arguments.toArray(String[]::new))
            .next();
    }

    private Stream<String> asArguments(Map<String, Long> values) {
        return values.entrySet()
            .stream()
            .flatMap(entry -> Stream.of(entry.getKey(), String.valueOf(entry.getValue())));
    }

    private String ttlSeconds() {
        return String.valueOf(configuration.randomizedTtl().getSeconds());
    }
}
