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

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

/**
 * Configuration of the Redis caches for mailbox counters and current quotas.
 *
 * Entries are stored for {@code ttl} plus a random duration between zero and {@code ttlJitter}, in order not to
 * have all entries expiring simultaneously.
 */
public class RedisMailboxCacheConfiguration {
    public static final String TTL_PROPERTY = "redis.cache.ttl";
    public static final String TTL_JITTER_PROPERTY = "redis.cache.ttl.jitter";
    public static final Duration DEFAULT_TTL = Duration.ofDays(1);
    public static final Duration DEFAULT_TTL_JITTER = Duration.ZERO;
    public static final RedisMailboxCacheConfiguration DEFAULT = new RedisMailboxCacheConfiguration(DEFAULT_TTL, DEFAULT_TTL_JITTER);

    public static RedisMailboxCacheConfiguration from(Configuration configuration) {
        Duration ttl = Optional.ofNullable(configuration.getString(TTL_PROPERTY, null))
            .map(DurationParser::parse)
            .orElse(DEFAULT_TTL);
        Duration ttlJitter = Optional.ofNullable(configuration.getString(TTL_JITTER_PROPERTY, null))
            .map(DurationParser::parse)
            .orElse(DEFAULT_TTL_JITTER);

        return new RedisMailboxCacheConfiguration(ttl, ttlJitter);
    }

    private final Duration ttl;
    private final Duration ttlJitter;

    public RedisMailboxCacheConfiguration(Duration ttl, Duration ttlJitter) {
        Preconditions.checkArgument(ttl.getSeconds() >= 1, "'%s' needs to be at least one second", TTL_PROPERTY);
        Preconditions.checkArgument(!ttlJitter.isNegative(), "'%s' needs to be positive", TTL_JITTER_PROPERTY);

        this.ttl = ttl;
        this.ttlJitter = ttlJitter;
    }

    public Duration getTtl() {
        return ttl;
    }

    public Duration getTtlJitter() {
        return ttlJitter;
    }

    public Duration randomizedTtl() {
        if (ttlJitter.isZero()) {
            return ttl;
        }
        return ttl.plusMillis(ThreadLocalRandom.current().nextLong(ttlJitter.toMillis() + 1));
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RedisMailboxCacheConfiguration that) {
            return Objects.equals(this.ttl, that.ttl)
                && Objects.equals(this.ttlJitter, that.ttlJitter);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(ttl, ttlJitter);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("ttl", ttl)
            .add("ttlJitter", ttlJitter)
            .toString();
    }
}
