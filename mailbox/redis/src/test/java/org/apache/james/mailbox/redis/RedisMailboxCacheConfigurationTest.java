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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class RedisMailboxCacheConfigurationTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(RedisMailboxCacheConfiguration.class)
            .verify();
    }

    @Test
    void fromShouldReturnDefaultWhenEmpty() {
        assertThat(RedisMailboxCacheConfiguration.from(new PropertiesConfiguration()))
            .isEqualTo(new RedisMailboxCacheConfiguration(Duration.ofDays(1), Duration.ZERO));
    }

    @Test
    void fromShouldParseValues() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("redis.cache.ttl", "6h");
        configuration.addProperty("redis.cache.ttl.jitter", "30m");

        assertThat(RedisMailboxCacheConfiguration.from(configuration))
            .isEqualTo(new RedisMailboxCacheConfiguration(Duration.ofHours(6), Duration.ofMinutes(30)));
    }

    @Test
    void shouldRejectTooSmallTtl() {
        assertThatThrownBy(() -> new RedisMailboxCacheConfiguration(Duration.ofMillis(10), Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeJitter() {
        assertThatThrownBy(() -> new RedisMailboxCacheConfiguration(Duration.ofHours(1), Duration.ofMinutes(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void randomizedTtlShouldReturnTtlWhenNoJitter() {
        assertThat(new RedisMailboxCacheConfiguration(Duration.ofHours(1), Duration.ZERO).randomizedTtl())
            .isEqualTo(Duration.ofHours(1));
    }

    @RepeatedTest(10)
    void randomizedTtlShouldApplyJitter() {
        assertThat(new RedisMailboxCacheConfiguration(Duration.ofHours(1), Duration.ofMinutes(10)).randomizedTtl())
            .isBetween(Duration.ofHours(1), Duration.ofMinutes(70));
    }
}
