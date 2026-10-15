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

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

/**
 * Configuration of a component cached in Redis (current quotas, mailbox counters), read from `redis.properties`:
 *
 *  - `{prefix}`: whether the component is cached in Redis. Defaults to false.
 *  - `{prefix}.timeout`: timeout of Redis commands, after which Redis is considered unavailable. Defaults to 10s.
 *  - `{prefix}.maintain.legacy`: whether legacy structures are maintained alongside Redis, enabling rollbacks and
 *  serving as fallback when Redis is unavailable. Defaults to false.
 */
public class RedisComponentConfiguration {
    public static final String CURRENT_QUOTA_PREFIX = "redis.current.quota";
    public static final String MAILBOX_COUNTERS_PREFIX = "redis.mailbox.counters";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    public static final RedisComponentConfiguration DISABLED = new RedisComponentConfiguration(false, DEFAULT_TIMEOUT, false);

    public static RedisComponentConfiguration from(Configuration configuration, String prefix) {
        return new RedisComponentConfiguration(
            configuration.getBoolean(prefix, false),
            Optional.ofNullable(configuration.getString(prefix + ".timeout", null))
                .map(DurationParser::parse)
                .orElse(DEFAULT_TIMEOUT),
            configuration.getBoolean(prefix + ".maintain.legacy", false));
    }

    public static RedisComponentConfiguration enabled() {
        return new RedisComponentConfiguration(true, DEFAULT_TIMEOUT, false);
    }

    private final boolean enabled;
    private final Duration timeout;
    private final boolean maintainLegacy;

    public RedisComponentConfiguration(boolean enabled, Duration timeout, boolean maintainLegacy) {
        Preconditions.checkArgument(timeout.isPositive(), "Redis timeout needs to be strictly positive");

        this.enabled = enabled;
        this.timeout = timeout;
        this.maintainLegacy = maintainLegacy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isMaintainLegacy() {
        return maintainLegacy;
    }

    public RedisComponentConfiguration withMaintainLegacy(boolean maintainLegacy) {
        return new RedisComponentConfiguration(enabled, timeout, maintainLegacy);
    }

    public RedisComponentConfiguration withTimeout(Duration timeout) {
        return new RedisComponentConfiguration(enabled, timeout, maintainLegacy);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RedisComponentConfiguration that) {
            return Objects.equals(this.enabled, that.enabled)
                && Objects.equals(this.timeout, that.timeout)
                && Objects.equals(this.maintainLegacy, that.maintainLegacy);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(enabled, timeout, maintainLegacy);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("enabled", enabled)
            .add("timeout", timeout)
            .add("maintainLegacy", maintainLegacy)
            .toString();
    }
}
