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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.pop3server.mailbox.Pop3MetadataModule.MAILBOX_ID;
import static org.apache.james.pop3server.mailbox.Pop3MetadataModule.MESSAGE_ID;
import static org.apache.james.pop3server.mailbox.Pop3MetadataModule.SIZE;
import static org.apache.james.pop3server.mailbox.Pop3MetadataModule.TABLE_NAME;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.reactivestreams.Publisher;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

public class CassandraPop3MetadataStore implements Pop3MetadataStore {
    private final CassandraAsyncExecutor executor;

    private final PreparedStatement list;
    private final PreparedStatement add;
    private final PreparedStatement remove;
    private final PreparedStatement clear;

    @Inject
    public CassandraPop3MetadataStore(Session session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.clear = prepareClear(session);
        this.list = prepareList(session);
        this.add = prepareAdd(session);
        this.remove = prepareRemove(session);
    }

    private PreparedStatement prepareRemove(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    private PreparedStatement prepareClear(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareAdd(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(SIZE, bindMarker(SIZE)));
    }

    private PreparedStatement prepareList(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    @Override
    public Publisher<StatMetadata> stat(MailboxId mailboxId) {
        CassandraId id = (CassandraId) mailboxId;

        return executor.executeRows(list.bind()
                .setUUID(MAILBOX_ID, id.asUuid()))
            .map(row -> new StatMetadata(
                CassandraMessageId.Factory.of(row.getUUID(MESSAGE_ID)),
                row.getLong(SIZE)));
    }

    @Override
    public Publisher<Void> add(MailboxId mailboxId, StatMetadata statMetadata) {
        CassandraId id = (CassandraId) mailboxId;
        CassandraMessageId messageId = (CassandraMessageId) statMetadata.getMessageId();

        return executor.executeVoid(add.bind()
            .setUUID(MAILBOX_ID, id.asUuid())
            .setUUID(MESSAGE_ID, messageId.get())
            .setLong(SIZE, statMetadata.getSize()));
    }

    @Override
    public Publisher<Void> remove(MailboxId mailboxId, StatMetadata statMetadata) {
        CassandraId id = (CassandraId) mailboxId;
        CassandraMessageId messageId = (CassandraMessageId) statMetadata.getMessageId();

        return executor.executeVoid(remove.bind()
            .setUUID(MAILBOX_ID, id.asUuid())
            .setUUID(MESSAGE_ID, messageId.get()));
    }

    @Override
    public Publisher<Void> clear(MailboxId mailboxId) {
        CassandraId id = (CassandraId) mailboxId;

        return executor.executeVoid(clear.bind()
            .setUUID(MAILBOX_ID, id.asUuid()));
    }
}
