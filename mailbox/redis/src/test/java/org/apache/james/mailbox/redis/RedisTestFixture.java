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

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.backends.redis.DockerRedis;
import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisReactiveCommandsFactory;
import org.apache.james.backends.redis.StandaloneRedisConfiguration;
import org.apache.james.server.core.filesystem.FileSystemImpl;

public class RedisTestFixture {
    public static final RedisMailboxCacheConfiguration CACHE_CONFIGURATION = new RedisMailboxCacheConfiguration(Duration.ofHours(1), Duration.ofMinutes(10));

    private static final ConcurrentHashMap<URI, RedisReactiveCommandsFactory> COMMANDS_FACTORIES = new ConcurrentHashMap<>();

    public static RedisCounterCache counterCache(DockerRedis dockerRedis) {
        RedisReactiveCommandsFactory commandsFactory = COMMANDS_FACTORIES.computeIfAbsent(dockerRedis.redisURI(), uri -> {
            StandaloneRedisConfiguration redisConfiguration = StandaloneRedisConfiguration.from(uri.toString());
            return new RedisReactiveCommandsFactory(
                new RedisClientFactory(FileSystemImpl.forTesting(), redisConfiguration), redisConfiguration);
        });
        return new RedisCounterCache(commandsFactory, CACHE_CONFIGURATION);
    }
}
