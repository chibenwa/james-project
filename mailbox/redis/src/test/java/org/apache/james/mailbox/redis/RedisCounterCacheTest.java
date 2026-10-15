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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.backends.redis.DockerRedis;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.lettuce.core.api.sync.RedisCommands;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RedisCounterCacheTest {
    private static final String KEY = "key";
    private static final ImmutableList<String> FIELDS = ImmutableList.of("a", "b");
    private static final Map<String, Long> VALUES = ImmutableMap.of("a", 1L, "b", 2L);
    private static final Map<String, Long> OTHER_VALUES = ImmutableMap.of("a", 10L, "b", 20L);
    private static final Duration TTL = RedisTestFixture.CACHE_CONFIGURATION.getTtl();
    private static final Duration TTL_JITTER = RedisTestFixture.CACHE_CONFIGURATION.getTtlJitter();

    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    private RedisCounterCache testee;
    private DockerRedis dockerRedis;
    private RedisCommands<String, String> redis;
    private AtomicInteger computationCount;

    @BeforeEach
    void setUp(DockerRedis dockerRedis) {
        testee = RedisTestFixture.counterCache(dockerRedis);
        this.dockerRedis = dockerRedis;
        redis = dockerRedis.createClient();
        computationCount = new AtomicInteger();
    }

    private Mono<Map<String, Long>> noFallback() {
        return Mono.error(new AssertionError("Fallback should not be called"));
    }

    private Mono<Map<String, Long>> compute(Map<String, Long> values) {
        return Mono.fromCallable(() -> {
            computationCount.incrementAndGet();
            return values;
        });
    }

    @Test
    void getOrComputeShouldComputeUponCacheMiss() {
        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block())
            .isEqualTo(VALUES);
    }

    @Test
    void getOrComputeShouldStoreComputedValues() {
        testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block();

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(OTHER_VALUES), this::noFallback).block())
            .isEqualTo(VALUES);
        assertThat(computationCount.get()).isEqualTo(1);
    }

    @Test
    void storedValuesShouldExpire() {
        testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block();

        assertThat(redis.ttl(KEY))
            .isBetween(TTL.getSeconds() - 5, TTL.plus(TTL_JITTER).getSeconds());
    }

    @Test
    void incrementIfPresentShouldIncrementStoredValues() {
        testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block();

        testee.incrementIfPresent(KEY, ImmutableMap.of("a", 3L, "b", -1L)).block();

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(OTHER_VALUES), this::noFallback).block())
            .isEqualTo(ImmutableMap.of("a", 4L, "b", 1L));
    }

    @Test
    void incrementIfPresentShouldBeNoopWhenAbsent() {
        testee.incrementIfPresent(KEY, ImmutableMap.of("a", 3L, "b", -1L)).block();

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block())
            .isEqualTo(VALUES);
    }

    @Test
    void incrementIfPresentShouldNotCreateEntriesWithoutTtl() {
        testee.incrementIfPresent(KEY, ImmutableMap.of("a", 3L, "b", -1L)).block();

        assertThat(redis.ttl(KEY)).isPositive();
    }

    @Test
    void incrementIfPresentShouldNotIncrementPartialEntries() {
        redis.hset(KEY, "a", "1");

        testee.incrementIfPresent(KEY, ImmutableMap.of("a", 3L, "b", -1L)).block();

        assertThat(redis.hget(KEY, "a")).isEqualTo("1");
        assertThat(redis.hexists(KEY, "b")).isFalse();
    }

    @Test
    void partialEntriesShouldBeRecomputed() {
        redis.hset(KEY, "a", "36");

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block())
            .isEqualTo(VALUES);
    }

    @Test
    void unreadableEntriesShouldBeRecomputed() {
        redis.hset(KEY, ImmutableMap.of("a", "36", "b", "notANumber"));

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block())
            .isEqualTo(VALUES);
    }

    @Test
    void recomputedValuesShouldBeStoredAfterPartialRead() {
        redis.hset(KEY, "a", "36");
        testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block();

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(OTHER_VALUES), this::noFallback).block())
            .isEqualTo(VALUES);
    }

    @Test
    void replaceShouldOverwriteStoredValues() {
        testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block();

        testee.replace(KEY, OTHER_VALUES).block();

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block())
            .isEqualTo(OTHER_VALUES);
    }

    @Test
    void replaceShouldStoreValuesWhenAbsent() {
        testee.replace(KEY, OTHER_VALUES).block();

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block())
            .isEqualTo(OTHER_VALUES);
        assertThat(computationCount.get()).isZero();
    }

    @Test
    void replaceShouldSetTtl() {
        testee.replace(KEY, OTHER_VALUES).block();

        assertThat(redis.ttl(KEY))
            .isBetween(TTL.getSeconds() - 5, TTL.plus(TTL_JITTER).getSeconds());
    }

    @Test
    void invalidateShouldTriggerRecomputation() {
        testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block();

        testee.invalidate(KEY).block();

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(OTHER_VALUES), this::noFallback).block())
            .isEqualTo(OTHER_VALUES);
    }

    @Test
    void invalidateShouldNotFailWhenAbsent() {
        testee.invalidate(KEY).block();

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block())
            .isEqualTo(VALUES);
    }

    @Test
    void recomputedValueShouldNotBeStoredWhenConcurrentIncrement() {
        // The increment happens while the recomputation is running: the recomputed value might not account for it
        Map<String, Long> result = testee.getOrCompute(KEY, FIELDS,
                () -> testee.incrementIfPresent(KEY, ImmutableMap.of("a", 3L, "b", 3L))
                    .then(compute(VALUES)), this::noFallback)
            .block();

        assertThat(result).isEqualTo(VALUES);
        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(OTHER_VALUES), this::noFallback).block())
            .isEqualTo(OTHER_VALUES);
    }

    @Test
    void recomputedValueShouldNotBeStoredWhenConcurrentReplace() {
        testee.getOrCompute(KEY, FIELDS,
                () -> testee.replace(KEY, OTHER_VALUES)
                    .then(compute(VALUES)), this::noFallback)
            .block();

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block())
            .isEqualTo(OTHER_VALUES);
    }

    @Test
    void recomputedValueShouldNotBeStoredWhenConcurrentInvalidation() {
        testee.getOrCompute(KEY, FIELDS,
                () -> testee.invalidate(KEY)
                    .then(compute(VALUES)), this::noFallback)
            .block();

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(OTHER_VALUES), this::noFallback).block())
            .isEqualTo(OTHER_VALUES);
    }

    @Test
    void concurrentRecomputationsShouldBeShared() {
        Mono<Map<String, Long>> slowComputation = compute(VALUES).delayElement(Duration.ofMillis(500));

        Flux.range(0, 10)
            .flatMap(any -> testee.getOrCompute(KEY, FIELDS, () -> slowComputation, this::noFallback))
            .collectList()
            .block();

        assertThat(computationCount.get()).isEqualTo(1);
    }

    @Test
    void failedRecomputationShouldNotBeCached() {
        testee.getOrCompute(KEY, FIELDS, () -> Mono.error(new RuntimeException()), this::noFallback)
            .onErrorResume(e -> Mono.empty())
            .block();

        assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), this::noFallback).block())
            .isEqualTo(VALUES);
    }

    @Nested
    class RedisFailures {
        @Test
        void getOrComputeShouldUseFallbackWhenRedisIsUnavailable() {
            RedisTestFixture.whileRedisIsPaused(dockerRedis, () ->
                assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), () -> Mono.just(OTHER_VALUES)).block())
                    .isEqualTo(OTHER_VALUES));
        }

        @Test
        void getOrComputeShouldNotComputeWhenRedisIsUnavailable() {
            RedisTestFixture.whileRedisIsPaused(dockerRedis, () ->
                testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), () -> Mono.just(OTHER_VALUES)).block());

            assertThat(computationCount.get()).isZero();
        }

        @Test
        void getOrComputeShouldReturnComputedValueWhenStoringItFails() {
            Mono<Map<String, Long>> computationPausingRedis = Mono.fromRunnable(dockerRedis::pause)
                .then(compute(VALUES));

            try {
                assertThat(testee.getOrCompute(KEY, FIELDS, () -> computationPausingRedis, RedisCounterCacheTest.this::noFallback).block())
                    .isEqualTo(VALUES);
            } finally {
                dockerRedis.unPause();
            }
        }

        @Test
        void incrementIfPresentShouldNotFailWhenRedisIsUnavailable() {
            testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), RedisCounterCacheTest.this::noFallback).block();

            RedisTestFixture.whileRedisIsPaused(dockerRedis, () ->
                assertThatCode(() -> testee.incrementIfPresent(KEY, ImmutableMap.of("a", 3L, "b", 3L)).block())
                    .doesNotThrowAnyException());
        }

        @Test
        void replaceShouldFailWhenRedisIsUnavailable() {
            RedisTestFixture.whileRedisIsPaused(dockerRedis, () ->
                assertThatThrownBy(() -> testee.replace(KEY, VALUES).block())
                    .isInstanceOf(Exception.class));
        }

        @Test
        void cacheShouldWorkAgainOnceRedisIsBack() {
            RedisTestFixture.whileRedisIsPaused(dockerRedis, () ->
                testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), () -> Mono.just(OTHER_VALUES)).block());

            assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), RedisCounterCacheTest.this::noFallback).block())
                .isEqualTo(VALUES);
        }
    }

    @Nested
    class Concurrency {
        private static final Map<String, Long> ZERO = ImmutableMap.of("a", 0L, "b", 0L);

        @Test
        void concurrentIncrementsShouldAllBeApplied() throws Exception {
            testee.getOrCompute(KEY, FIELDS, () -> compute(ZERO), RedisCounterCacheTest.this::noFallback).block();

            ConcurrentTestRunner.builder()
                .reactorOperation((threadNumber, step) -> testee.incrementIfPresent(KEY, ImmutableMap.of("a", 1L, "b", 2L)))
                .threadCount(10)
                .operationCount(100)
                .runSuccessfullyWithin(Duration.ofMinutes(1));

            assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), RedisCounterCacheTest.this::noFallback).block())
                .isEqualTo(ImmutableMap.of("a", 1000L, "b", 2000L));
        }

        @Test
        void concurrentIncrementsAndDecrementsShouldAllBeApplied() throws Exception {
            testee.getOrCompute(KEY, FIELDS, () -> compute(ZERO), RedisCounterCacheTest.this::noFallback).block();

            ConcurrentTestRunner.builder()
                .reactorOperation((threadNumber, step) -> {
                    long delta = threadNumber % 2 == 0 ? 1L : -1L;
                    return testee.incrementIfPresent(KEY, ImmutableMap.of("a", delta, "b", delta));
                })
                .threadCount(10)
                .operationCount(100)
                .runSuccessfullyWithin(Duration.ofMinutes(1));

            assertThat(testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), RedisCounterCacheTest.this::noFallback).block())
                .isEqualTo(ZERO);
        }

        @Test
        void concurrentIncrementsOnDistinctKeysShouldNotInterfere() throws Exception {
            for (int i = 0; i < 10; i++) {
                testee.getOrCompute(KEY + i, FIELDS, () -> compute(ZERO), RedisCounterCacheTest.this::noFallback).block();
            }

            ConcurrentTestRunner.builder()
                .reactorOperation((threadNumber, step) -> testee.incrementIfPresent(KEY + threadNumber, ImmutableMap.of("a", 1L, "b", 1L)))
                .threadCount(10)
                .operationCount(100)
                .runSuccessfullyWithin(Duration.ofMinutes(1));

            for (int i = 0; i < 10; i++) {
                assertThat(testee.getOrCompute(KEY + i, FIELDS, () -> compute(VALUES), RedisCounterCacheTest.this::noFallback).block())
                    .isEqualTo(ImmutableMap.of("a", 100L, "b", 100L));
            }
        }

        @Test
        void concurrentReadsShouldReturnTheComputedValue() throws Exception {
            ConcurrentLinkedQueue<Map<String, Long>> results = new ConcurrentLinkedQueue<>();

            ConcurrentTestRunner.builder()
                .reactorOperation((threadNumber, step) -> testee.getOrCompute(KEY, FIELDS,
                        () -> compute(VALUES).delayElement(Duration.ofMillis(10)), RedisCounterCacheTest.this::noFallback)
                    .doOnNext(results::add)
                    .then())
                .threadCount(10)
                .operationCount(10)
                .runSuccessfullyWithin(Duration.ofMinutes(1));

            assertThat(results).hasSize(100).containsOnly(VALUES);
            assertThat(computationCount.get()).isLessThan(10);
        }

        @Test
        void concurrentWritesShouldNeverLeavePartialEntries() throws Exception {
            ConcurrentTestRunner.builder()
                .randomlyDistributedReactorOperations(
                    (threadNumber, step) -> testee.incrementIfPresent(KEY, ImmutableMap.of("a", 1L, "b", 1L)),
                    (threadNumber, step) -> testee.replace(KEY, VALUES),
                    (threadNumber, step) -> testee.invalidate(KEY),
                    (threadNumber, step) -> testee.getOrCompute(KEY, FIELDS, () -> compute(VALUES), RedisCounterCacheTest.this::noFallback).then())
                .threadCount(10)
                .operationCount(100)
                .runSuccessfullyWithin(Duration.ofMinutes(1));

            Map<String, String> stored = redis.hgetall(KEY);
            boolean complete = stored.containsKey("a") && stored.containsKey("b");
            boolean onlyGeneration = !stored.containsKey("a") && !stored.containsKey("b");
            assertThat(complete || onlyGeneration)
                .as("Entry %s should either be complete or invalidated", stored)
                .isTrue();
            assertThat(redis.ttl(KEY)).isPositive();
        }
    }
}
