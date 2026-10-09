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

import java.io.FileNotFoundException;
import java.util.Objects;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
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
    public static final String CURRENT_QUOTA_PROPERTY = "redis.current.quota";
    public static final String MAILBOX_COUNTERS_PROPERTY = "redis.mailbox.counters";
    public static final RedisMailboxConfiguration DISABLED = new RedisMailboxConfiguration(false, false);

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
            configuration.getBoolean(CURRENT_QUOTA_PROPERTY, false),
            configuration.getBoolean(MAILBOX_COUNTERS_PROPERTY, false));
    }

    private final boolean currentQuotaEnabled;
    private final boolean mailboxCountersEnabled;

    public RedisMailboxConfiguration(boolean currentQuotaEnabled, boolean mailboxCountersEnabled) {
        this.currentQuotaEnabled = currentQuotaEnabled;
        this.mailboxCountersEnabled = mailboxCountersEnabled;
    }

    public boolean isCurrentQuotaEnabled() {
        return currentQuotaEnabled;
    }

    public boolean isMailboxCountersEnabled() {
        return mailboxCountersEnabled;
    }

    public boolean isEnabled() {
        return currentQuotaEnabled || mailboxCountersEnabled;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RedisMailboxConfiguration that) {
            return Objects.equals(this.currentQuotaEnabled, that.currentQuotaEnabled)
                && Objects.equals(this.mailboxCountersEnabled, that.mailboxCountersEnabled);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(currentQuotaEnabled, mailboxCountersEnabled);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("currentQuotaEnabled", currentQuotaEnabled)
            .add("mailboxCountersEnabled", mailboxCountersEnabled)
            .toString();
    }
}
