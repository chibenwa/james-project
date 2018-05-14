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

package org.apache.james.modules;

import org.apache.james.eventsourcing.CommandDispatcher;
import org.apache.james.eventsourcing.CommandHandler;
import org.apache.james.eventsourcing.EventBus;
import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.EventSourcingSystemImpl;
import org.apache.james.eventsourcing.Subscriber;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class EventSourcingModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(EventSourcingSystemImpl.class).in(Scopes.SINGLETON);
        bind(CommandDispatcher.class).in(Scopes.SINGLETON);
        bind(EventBus.class).in(Scopes.SINGLETON);

        bind(EventSourcingSystem.class).to(EventSourcingSystemImpl.class);

        Multibinder.newSetBinder(binder(), Subscriber.class);
        Multibinder.newSetBinder(binder(), CommandHandler.class);
    }
}
