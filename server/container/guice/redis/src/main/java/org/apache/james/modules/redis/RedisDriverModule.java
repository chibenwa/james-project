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

package org.apache.james.modules.redis;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.backends.redis.RedisHealthCheck;
import org.apache.james.backends.redis.RedisReactiveCommandsFactory;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

/**
 * Binds the Redis driver, configured by `redis.properties`.
 *
 * Install it only once per injector: components relying on Redis (rate limiting, mailbox caches...)
 * are expected to depend on these bindings without redefining them.
 */
public class RedisDriverModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(RedisClientFactory.class).in(Scopes.SINGLETON);
        bind(RedisReactiveCommandsFactory.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding()
            .to(RedisHealthCheck.class);
    }

    @Provides
    @Singleton
    RedisConfiguration provideConfig(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        return RedisConfiguration.from(propertiesProvider.getConfiguration("redis"));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RedisDriverModule;
    }

    @Override
    public int hashCode() {
        return RedisDriverModule.class.hashCode();
    }
}
