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

import org.apache.james.events.EventListener;
import org.apache.james.mailbox.redis.mail.RedisMailboxCountersListener;
import org.apache.james.mailbox.redis.mail.RedisMailboxCountersStore;
import org.apache.james.mailbox.store.mail.MailboxCountersStore;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class RedisMailboxCountersModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(RedisMailboxCountersStore.class).in(Scopes.SINGLETON);
        bind(MailboxCountersStore.class).to(RedisMailboxCountersStore.class);

        bind(RedisMailboxCountersListener.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding()
            .to(RedisMailboxCountersListener.class);
    }
}
