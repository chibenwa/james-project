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

package org.apache.james;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.NEXT_MODSEQ;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.TABLE_NAME;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Mono;

public class NoLWTCassandraModSeqProvider extends CassandraModSeqProvider {

    @Inject
    public NoLWTCassandraModSeqProvider(Session session, CassandraConfiguration cassandraConfiguration,
                                        CassandraConsistenciesConfiguration consistenciesConfiguration) {
        super(session, cassandraConfiguration, consistenciesConfiguration);
    }

    @Override
    protected PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(NEXT_MODSEQ, bindMarker(NEXT_MODSEQ))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID)));
    }

    @Override
    protected PreparedStatement prepareUpdate(Session session) {
        return session.prepare(update(TABLE_NAME)
            .with(set(NEXT_MODSEQ, bindMarker(NEXT_MODSEQ)))
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    @Override
    protected PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(NEXT_MODSEQ)
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    @Override
    protected Mono<ModSeq> tryInsertModSeq(CassandraId mailboxId, ModSeq modSeq) {
        ModSeq nextModSeq = modSeq.next();
        return cassandraAsyncExecutor.executeReturnApplied(
            update.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(NEXT_MODSEQ, nextModSeq.asLong()))
            .map(success -> successToModSeq(nextModSeq, success))
            .handle(publishIfPresent());
    }

    @Override
    protected Mono<Optional<ModSeq>> findHighestModSeq(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeSingleRowOptional(
            select.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .map(maybeRow -> maybeRow.map(row -> ModSeq.of(row.getLong(NEXT_MODSEQ))));
    }
}
