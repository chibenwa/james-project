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

package org.apache.james.modules.mailbox.redis;

import static org.apache.james.mailbox.redis.RedisComponentConfiguration.CURRENT_QUOTA_PREFIX;
import static org.apache.james.mailbox.redis.RedisComponentConfiguration.MAILBOX_COUNTERS_PREFIX;

import java.io.FileNotFoundException;
import java.util.Objects;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.mailbox.redis.RedisComponentConfiguration;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

/**
 * Which mailbox components are cached in Redis, as configured in `redis.properties`.
 */
public class RedisMailboxConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMailboxConfiguration.class);
    private static final String REDIS_CONFIGURATION_NAME = "redis";
    public static final RedisMailboxConfiguration DISABLED = new RedisMailboxConfiguration(RedisComponentConfiguration.DISABLED, RedisComponentConfiguration.DISABLED);

    public static RedisMailboxConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return from(propertiesProvider.getConfiguration(REDIS_CONFIGURATION_NAME));
        } catch (FileNotFoundException e) {
            LOGGER.debug("Could not find {} configuration file, Redis mailbox caches are disabled", REDIS_CONFIGURATION_NAME);
            return DISABLED;
        }
    }

    public static RedisMailboxConfiguration from(Configuration configuration) {
        return new RedisMailboxConfiguration(
            RedisComponentConfiguration.from(configuration, CURRENT_QUOTA_PREFIX),
            RedisComponentConfiguration.from(configuration, MAILBOX_COUNTERS_PREFIX));
    }

    private final RedisComponentConfiguration currentQuota;
    private final RedisComponentConfiguration mailboxCounters;

    public RedisMailboxConfiguration(RedisComponentConfiguration currentQuota, RedisComponentConfiguration mailboxCounters) {
        this.currentQuota = currentQuota;
        this.mailboxCounters = mailboxCounters;
    }

    public RedisMailboxConfiguration(boolean currentQuotaEnabled, boolean mailboxCountersEnabled) {
        this(currentQuotaEnabled ? RedisComponentConfiguration.enabled() : RedisComponentConfiguration.DISABLED,
            mailboxCountersEnabled ? RedisComponentConfiguration.enabled() : RedisComponentConfiguration.DISABLED);
    }

    public RedisComponentConfiguration getCurrentQuota() {
        return currentQuota;
    }

    public RedisComponentConfiguration getMailboxCounters() {
        return mailboxCounters;
    }

    public boolean isEnabled() {
        return currentQuota.isEnabled() || mailboxCounters.isEnabled();
    }

    /**
     * @return true if mailbox counters are stored in Redis only, and legacy counters thus no longer need maintenance.
     */
    public boolean isLegacyMailboxCountersObsolete() {
        return mailboxCounters.isEnabled() && !mailboxCounters.isMaintainLegacy();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RedisMailboxConfiguration that) {
            return Objects.equals(this.currentQuota, that.currentQuota)
                && Objects.equals(this.mailboxCounters, that.mailboxCounters);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(currentQuota, mailboxCounters);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("currentQuota", currentQuota)
            .add("mailboxCounters", mailboxCounters)
            .toString();
    }
}
