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

package org.apache.james.mailbox.events.eventsourcing;

import static org.apache.james.mailbox.events.eventsourcing.RegisteredGroupsSubscriber.Registrer;

import java.time.Clock;

import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.eventsourcing.RegisteredGroupsSubscriber.RegisteredGroupsProvider;
import org.apache.james.mailbox.events.eventsourcing.RegisteredGroupsSubscriber.Unregisterer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class GroupUnregistringManager {
    private final EventSourcingSystem eventSourcingSystem;
    private final EventStore eventStore;

    public GroupUnregistringManager(EventStore eventStore,
                                    Unregisterer unregisterer,
                                    Registrer registrer,
                                    RegisteredGroupsProvider registeredGroupsProvider,
                                    Clock clock) {

        this.eventStore = eventStore;
        this.eventSourcingSystem = EventSourcingSystem.fromJava(ImmutableSet.of(new RequireGroupsCommandHandler(eventStore, clock)),
            ImmutableSet.of(new RegisteredGroupsSubscriber(unregisterer, registrer, registeredGroupsProvider)),
            this.eventStore);
    }

    public Mono<Void> start(ImmutableSet<Group> groups) {
        RequireGroupsCommand requireGroupsCommand = new RequireGroupsCommand(groups);

        return Mono.from(eventSourcingSystem.dispatch(requireGroupsCommand));
    }

    @VisibleForTesting
    Mono<ImmutableSet<Group>> requiredGroups() {
        return Mono.from(eventStore.getEventsOfAggregate(RegisteredGroupsAggregate.AGGREGATE_ID))
            .map(RegisteredGroupsAggregate::load)
            .map(RegisteredGroupsAggregate::requiredGroups);
    }
}
