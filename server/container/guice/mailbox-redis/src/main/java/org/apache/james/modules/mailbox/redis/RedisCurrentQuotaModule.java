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

import java.util.Optional;

import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.redis.RedisComponentConfiguration;
import org.apache.james.mailbox.redis.RedisCounterCache;
import org.apache.james.mailbox.redis.quota.RedisCurrentQuotaManager;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class RedisCurrentQuotaModule extends AbstractModule {
    private final RedisComponentConfiguration configuration;
    private final Class<? extends CurrentQuotaManager> legacyImplementation;
    private Provider<? extends CurrentQuotaManager> legacyProvider;

    /**
     * @param legacyImplementation current quota manager maintained alongside Redis when legacy is maintained
     */
    public RedisCurrentQuotaModule(RedisComponentConfiguration configuration, Class<? extends CurrentQuotaManager> legacyImplementation) {
        this.configuration = configuration;
        this.legacyImplementation = legacyImplementation;
    }

    @Override
    protected void configure() {
        bind(CurrentQuotaManager.class).to(RedisCurrentQuotaManager.class);
        legacyProvider = getProvider(legacyImplementation);
    }

    @Provides
    @Singleton
    RedisCurrentQuotaManager provideRedisCurrentQuotaManager(RedisCounterCache.Factory cacheFactory, CurrentQuotaCalculator currentQuotaCalculator,
                                                             QuotaRootResolver quotaRootResolver, SessionProvider sessionProvider) {
        Optional<CurrentQuotaManager> legacy = Optional.of(configuration)
            .filter(RedisComponentConfiguration::isMaintainLegacy)
            .map(any -> legacyProvider.get());

        return new RedisCurrentQuotaManager(cacheFactory.create(configuration.getTimeout()),
            currentQuotaCalculator, quotaRootResolver, sessionProvider, legacy);
    }
}
