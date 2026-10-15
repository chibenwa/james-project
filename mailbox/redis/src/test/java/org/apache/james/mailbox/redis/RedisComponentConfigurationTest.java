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
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class RedisComponentConfigurationTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(RedisComponentConfiguration.class)
            .verify();
    }

    @Test
    void fromShouldReturnDisabledByDefault() {
        assertThat(RedisComponentConfiguration.from(new PropertiesConfiguration(), RedisComponentConfiguration.CURRENT_QUOTA_PREFIX))
            .isEqualTo(RedisComponentConfiguration.DISABLED);
    }

    @Test
    void fromShouldParseAllProperties() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("redis.mailbox.counters", "true");
        configuration.addProperty("redis.mailbox.counters.timeout", "500ms");
        configuration.addProperty("redis.mailbox.counters.maintain.legacy", "true");

        assertThat(RedisComponentConfiguration.from(configuration, RedisComponentConfiguration.MAILBOX_COUNTERS_PREFIX))
            .isEqualTo(new RedisComponentConfiguration(true, Duration.ofMillis(500), true));
    }

    @Test
    void fromShouldNotMixComponents() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("redis.mailbox.counters", "true");
        configuration.addProperty("redis.mailbox.counters.maintain.legacy", "true");

        assertThat(RedisComponentConfiguration.from(configuration, RedisComponentConfiguration.CURRENT_QUOTA_PREFIX))
            .isEqualTo(RedisComponentConfiguration.DISABLED);
    }

    @Test
    void shouldRejectZeroTimeout() {
        assertThatThrownBy(() -> new RedisComponentConfiguration(true, Duration.ZERO, false))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
