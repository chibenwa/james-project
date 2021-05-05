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

package org.apache.james.pop3server.mailbox;

import java.util.function.Function;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;

import reactor.core.publisher.Mono;

public class MemoryPop3MetadataStore implements Pop3MetadataStore {
    private final Table<MailboxId, MessageId, Long> data;

    public MemoryPop3MetadataStore() {
        data = HashBasedTable.create();
    }

    @Override
    public Publisher<StatMetadata> stat(MailboxId mailboxId) {
        return Mono.fromCallable(() -> ImmutableList.copyOf(data.row(mailboxId).entrySet()))
            .flatMapIterable(Function.identity())
            .map(entry -> new StatMetadata(entry.getKey(), entry.getValue()));
    }

    @Override
    public Publisher<Void> add(MailboxId mailboxId, StatMetadata statMetadata) {
        return Mono.fromRunnable(() -> data.put(mailboxId, statMetadata.getMessageId(), statMetadata.getSize()));
    }

    @Override
    public Publisher<Void> remove(MailboxId mailboxId, StatMetadata statMetadata) {
        return Mono.fromRunnable(() -> data.remove(mailboxId, statMetadata.getMessageId()));
    }

    @Override
    public Publisher<Void> clear(MailboxId mailboxId) {
        return Mono.fromRunnable(() -> data.row(mailboxId).clear());
    }
}
