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

import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.store.mail.MailboxCountersStore;
import org.apache.james.modules.redis.RedisDriverModule;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.google.inject.util.Modules;

/**
 * Substitutes Redis caches to the default implementations of current quotas and mailbox counters.
 *
 * The returned module is meant to override the mailbox modules.
 */
public class RedisMailboxModuleChooser {
    /**
     * Implementations maintained alongside Redis when `maintain.legacy` is enabled.
     */
    public record LegacyImplementations(Class<? extends CurrentQuotaManager> currentQuotaManager,
                                        Class<? extends MailboxCountersStore> mailboxCountersStore) {

    }

    public static Module chooseModules(RedisMailboxConfiguration configuration, LegacyImplementations legacyImplementations) {
        if (!configuration.isEnabled()) {
            return Modules.EMPTY_MODULE;
        }

        ImmutableList.Builder<Module> modules = ImmutableList.<Module>builder()
            .add(new RedisDriverModule())
            .add(new RedisMailboxCacheModule());
        if (configuration.getCurrentQuota().isEnabled()) {
            modules.add(new RedisCurrentQuotaModule(configuration.getCurrentQuota(), legacyImplementations.currentQuotaManager()));
        }
        if (configuration.getMailboxCounters().isEnabled()) {
            modules.add(new RedisMailboxCountersModule(configuration.getMailboxCounters(), legacyImplementations.mailboxCountersStore()));
        }
        return Modules.combine(modules.build());
    }
}
