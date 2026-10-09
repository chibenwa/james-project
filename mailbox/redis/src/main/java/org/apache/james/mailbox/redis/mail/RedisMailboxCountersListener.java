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

package org.apache.james.mailbox.redis.mail;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.events.MailboxEvents.MetaDataHoldingEvent;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

/**
 * Maintains mailbox counters cached in Redis upon mailbox events.
 *
 * In a distributed setup, this listener should be executed synchronously, in order to read up to date counters
 * right after a write. See the `james.eventbus.synchronous.listener.groups` JVM property.
 */
public class RedisMailboxCountersListener implements EventListener.ReactiveGroupEventListener {
    public static class RedisMailboxCountersListenerGroup extends Group {

    }

    private static final Group GROUP = new RedisMailboxCountersListenerGroup();

    private final RedisMailboxCountersStore countersStore;

    @Inject
    public RedisMailboxCountersListener(RedisMailboxCountersStore countersStore) {
        this.countersStore = countersStore;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof Added
            || event instanceof Expunged
            || event instanceof FlagsUpdated
            || event instanceof MailboxDeletion;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        return switch (event) {
            case Added added -> countersStore.applyDelta(added.getMailboxId(), messageCount(added), unseenCount(added));
            case Expunged expunged -> countersStore.applyDelta(expunged.getMailboxId(), -messageCount(expunged), -unseenCount(expunged));
            case FlagsUpdated flagsUpdated -> countersStore.applyDelta(flagsUpdated.getMailboxId(), 0, unseenDelta(flagsUpdated));
            case MailboxDeletion mailboxDeletion -> countersStore.delete(mailboxDeletion.getMailboxId());
            default -> Mono.empty();
        };
    }

    private long messageCount(MetaDataHoldingEvent event) {
        return event.getUids().size();
    }

    private long unseenCount(MetaDataHoldingEvent event) {
        return event.getUids()
            .stream()
            .filter(uid -> !event.getMetaData(uid).getFlags().contains(Flags.Flag.SEEN))
            .count();
    }

    private long unseenDelta(FlagsUpdated flagsUpdated) {
        return flagsUpdated.getUpdatedFlags()
            .stream()
            .mapToLong(this::unseenDelta)
            .sum();
    }

    private long unseenDelta(UpdatedFlags updatedFlags) {
        if (updatedFlags.isModifiedToUnset(Flags.Flag.SEEN)) {
            return 1;
        }
        if (updatedFlags.isModifiedToSet(Flags.Flag.SEEN)) {
            return -1;
        }
        return 0;
    }
}
