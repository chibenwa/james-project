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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class RedisMailboxConfigurationTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(RedisMailboxConfiguration.class)
            .verify();
    }

    @Test
    void fromShouldDisableCachesByDefault() {
        assertThat(RedisMailboxConfiguration.from(new PropertiesConfiguration()))
            .isEqualTo(RedisMailboxConfiguration.DISABLED);
    }

    @Test
    void fromShouldParseCurrentQuota() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("redis.current.quota", "true");

        assertThat(RedisMailboxConfiguration.from(configuration))
            .isEqualTo(new RedisMailboxConfiguration(true, false));
    }

    @Test
    void fromShouldParseMailboxCounters() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("redis.mailbox.counters", "true");

        assertThat(RedisMailboxConfiguration.from(configuration))
            .isEqualTo(new RedisMailboxConfiguration(false, true));
    }

    @Test
    void isEnabledShouldBeFalseWhenDisabled() {
        assertThat(RedisMailboxConfiguration.DISABLED.isEnabled()).isFalse();
    }

    @Test
    void isEnabledShouldBeTrueWhenOneCacheIsEnabled() {
        assertThat(new RedisMailboxConfiguration(false, true).isEnabled()).isTrue();
    }
}
