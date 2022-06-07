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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static com.datastax.oss.driver.api.querybuilder.update.Assignment.setColumn;
import static org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles.ConsistencyChoice.STRONG;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.IMAP_UID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MAILBOX_ID_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_START_OCTET;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_START_OCTET_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.FULL_CONTENT_OCTETS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.FULL_CONTENT_OCTETS_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.HEADER_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.HEADER_CONTENT_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.INTERNAL_DATE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.INTERNAL_DATE_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.Flag.ANSWERED;
import static org.apache.james.mailbox.cassandra.table.Flag.DELETED;
import static org.apache.james.mailbox.cassandra.table.Flag.DRAFT;
import static org.apache.james.mailbox.cassandra.table.Flag.FLAGGED;
import static org.apache.james.mailbox.cassandra.table.Flag.RECENT;
import static org.apache.james.mailbox.cassandra.table.Flag.SEEN;
import static org.apache.james.mailbox.cassandra.table.Flag.USER;
import static org.apache.james.mailbox.cassandra.table.Flag.USER_FLAGS;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.MOD_SEQ;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.MOD_SEQ_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.THREAD_ID;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.THREAD_ID_LOWERCASE;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.MutableBoundStatementWrapper;
import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UpdatedFlags;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.update.Update;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMessageIdToImapUidDAO {
    private static final String MOD_SEQ_CONDITION = "modSeqCondition";
    private static final String ADDED_USERS_FLAGS = "added_user_flags";
    private static final String REMOVED_USERS_FLAGS = "removed_user_flags";

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final BlobId.Factory blobIdFactory;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement insertForced;
    private final PreparedStatement update;
    private final PreparedStatement selectAll;
    private final PreparedStatement select;
    private final PreparedStatement listStatement;
    private final CassandraConfiguration cassandraConfiguration;
    private final CqlSession session;
    private final DriverExecutionProfile lwtProfile;

    @Inject
    public CassandraMessageIdToImapUidDAO(CqlSession session, BlobId.Factory blobIdFactory,
                                          CassandraConfiguration cassandraConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.blobIdFactory = blobIdFactory;
        this.cassandraConfiguration = cassandraConfiguration;
        this.delete = prepareDelete();
        this.insert = prepareInsert();
        this.insertForced = prepareInsertForced();
        this.update = prepareUpdate();
        this.selectAll = prepareSelectAll();
        this.select = prepareSelect();
        this.listStatement = prepareList();
        this.session = session;
        this.lwtProfile = JamesExecutionProfiles.getLWTProfile(session);
    }

    private PreparedStatement prepareDelete() {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(List.of(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)),
                column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))))
            .build());
    }

    private PreparedStatement prepareInsert() {
        Insert insert = insertInto(TABLE_NAME)
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(IMAP_UID, bindMarker(IMAP_UID))
            .value(THREAD_ID, bindMarker(THREAD_ID))
            .value(MOD_SEQ, bindMarker(MOD_SEQ))
            .value(ANSWERED, bindMarker(ANSWERED))
            .value(DELETED, bindMarker(DELETED))
            .value(DRAFT, bindMarker(DRAFT))
            .value(FLAGGED, bindMarker(FLAGGED))
            .value(RECENT, bindMarker(RECENT))
            .value(SEEN, bindMarker(SEEN))
            .value(USER, bindMarker(USER))
            .value(USER_FLAGS, bindMarker(USER_FLAGS))
            .value(INTERNAL_DATE, bindMarker(INTERNAL_DATE))
            .value(BODY_START_OCTET, bindMarker(BODY_START_OCTET))
            .value(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS))
            .value(HEADER_CONTENT, bindMarker(HEADER_CONTENT));
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return session.prepare(insert.ifNotExists().build());
        } else {
            return session.prepare(QueryBuilder.update(TABLE_NAME)
                .set(setColumn(THREAD_ID, bindMarker(THREAD_ID)),
                    setColumn(MOD_SEQ, bindMarker(MOD_SEQ)),
                    setColumn(ANSWERED, bindMarker(ANSWERED)),
                    setColumn(DELETED, bindMarker(DELETED)),
                    setColumn(DRAFT, bindMarker(DRAFT)),
                    setColumn(FLAGGED, bindMarker(FLAGGED)),
                    setColumn(RECENT, bindMarker(RECENT)),
                    setColumn(SEEN, bindMarker(SEEN)),
                    setColumn(USER, bindMarker(USER)),
                    setColumn(INTERNAL_DATE, bindMarker(INTERNAL_DATE)),
                    setColumn(BODY_START_OCTET, bindMarker(BODY_START_OCTET)),
                    setColumn(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS)),
                    setColumn(HEADER_CONTENT, bindMarker(HEADER_CONTENT)))
                .appendSetElement(USER_FLAGS, bindMarker(USER_FLAGS))
                .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)),
                    column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                    column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
                .build());
        }
    }

    private PreparedStatement prepareInsertForced() {
        Insert insert = insertInto(TABLE_NAME)
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(IMAP_UID, bindMarker(IMAP_UID))
            .value(MOD_SEQ, bindMarker(MOD_SEQ))
            .value(ANSWERED, bindMarker(ANSWERED))
            .value(DELETED, bindMarker(DELETED))
            .value(DRAFT, bindMarker(DRAFT))
            .value(FLAGGED, bindMarker(FLAGGED))
            .value(RECENT, bindMarker(RECENT))
            .value(SEEN, bindMarker(SEEN))
            .value(USER, bindMarker(USER))
            .value(USER_FLAGS, bindMarker(USER_FLAGS))
            .value(INTERNAL_DATE, bindMarker(INTERNAL_DATE))
            .value(BODY_START_OCTET, bindMarker(BODY_START_OCTET))
            .value(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS))
            .value(HEADER_CONTENT, bindMarker(HEADER_CONTENT));
        return session.prepare(insert.build());
    }

    private PreparedStatement prepareUpdate() {
        Update update = QueryBuilder.update(TABLE_NAME)
            .set(setColumn(MOD_SEQ, bindMarker(MOD_SEQ)),
                setColumn(ANSWERED, bindMarker(ANSWERED)),
                setColumn(DELETED, bindMarker(DELETED)),
                setColumn(DRAFT, bindMarker(DRAFT)),
                setColumn(FLAGGED, bindMarker(FLAGGED)),
                setColumn(RECENT, bindMarker(RECENT)),
                setColumn(SEEN, bindMarker(SEEN)),
                setColumn(USER, bindMarker(USER)))
            .appendSetElement(USER_FLAGS, bindMarker(ADDED_USERS_FLAGS))
            .removeSetElement(USER_FLAGS, bindMarker(REMOVED_USERS_FLAGS))
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)),
                column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)));

        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return session.prepare(update.ifColumn(MOD_SEQ).isEqualTo(bindMarker(MOD_SEQ_CONDITION)).build());
        } else {
            return session.prepare(update.build());
        }
    }

    private PreparedStatement prepareSelectAll() {
        return session.prepare(selectFrom(TABLE_NAME)
            .all()
            .where(column(MESSAGE_ID_LOWERCASE).isEqualTo(bindMarker(MESSAGE_ID_LOWERCASE)))
            .build());
    }

    private PreparedStatement prepareList() {
        return session.prepare(selectFrom(TABLE_NAME).all().build());
    }

    private PreparedStatement prepareSelect() {
        return session.prepare(selectFrom(TABLE_NAME)
            .all()
            .where(column(MESSAGE_ID_LOWERCASE).isEqualTo(bindMarker(MESSAGE_ID_LOWERCASE)),
                column(MAILBOX_ID_LOWERCASE).isEqualTo(bindMarker(MAILBOX_ID_LOWERCASE)))
            .build());
    }

    public Mono<Void> delete(CassandraMessageId messageId, CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
            .setUuid(MESSAGE_ID, messageId.get())
            .setUuid(MAILBOX_ID, mailboxId.asUuid()));
    }

    public Mono<Void> insert(CassandraMessageMetadata metadata) {
        ComposedMessageId composedMessageId = metadata.getComposedMessageId().getComposedMessageId();
        Flags flags = metadata.getComposedMessageId().getFlags();
        ThreadId threadId = metadata.getComposedMessageId().getThreadId();

        BoundStatement boundStatement = insert.bind();
        MutableBoundStatementWrapper statementWrapper = new MutableBoundStatementWrapper(boundStatement);
        if (metadata.getComposedMessageId().getFlags().getUserFlags().length == 0) {
            statementWrapper.setNewStatement(statementWrapper.getStatement().unset(USER_FLAGS));
        } else {
            statementWrapper.setNewStatement(statementWrapper.getStatement().setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()), String.class));
        }

        return cassandraAsyncExecutor.executeVoid(statementWrapper.getStatement()
            .setUuid(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get())
            .setUuid(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid())
            .setLong(IMAP_UID, composedMessageId.getUid().asLong())
            .setLong(MOD_SEQ, metadata.getComposedMessageId().getModSeq().asLong())
            .setUuid(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get())
            .setBoolean(ANSWERED, flags.contains(Flag.ANSWERED))
            .setBoolean(DELETED, flags.contains(Flag.DELETED))
            .setBoolean(DRAFT, flags.contains(Flag.DRAFT))
            .setBoolean(FLAGGED, flags.contains(Flag.FLAGGED))
            .setBoolean(RECENT, flags.contains(Flag.RECENT))
            .setBoolean(SEEN, flags.contains(Flag.SEEN))
            .setBoolean(USER, flags.contains(Flag.USER))
            .setInstant(INTERNAL_DATE, metadata.getInternalDate().get().toInstant())
            .setInt(BODY_START_OCTET, Math.toIntExact(metadata.getBodyStartOctet().get()))
            .setLong(FULL_CONTENT_OCTETS, metadata.getSize().get())
            .setString(HEADER_CONTENT, metadata.getHeaderContent().get().asString()));
    }

    public Mono<Void> insertForce(CassandraMessageMetadata metadata) {
        ComposedMessageId composedMessageId = metadata.getComposedMessageId().getComposedMessageId();
        Flags flags = metadata.getComposedMessageId().getFlags();
        return cassandraAsyncExecutor.executeVoid(insertForced.bind()
            .setUuid(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get())
            .setUuid(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid())
            .setLong(IMAP_UID, composedMessageId.getUid().asLong())
            .setLong(MOD_SEQ, metadata.getComposedMessageId().getModSeq().asLong())
            .setBoolean(ANSWERED, flags.contains(Flag.ANSWERED))
            .setBoolean(DELETED, flags.contains(Flag.DELETED))
            .setBoolean(DRAFT, flags.contains(Flag.DRAFT))
            .setBoolean(FLAGGED, flags.contains(Flag.FLAGGED))
            .setBoolean(RECENT, flags.contains(Flag.RECENT))
            .setBoolean(SEEN, flags.contains(Flag.SEEN))
            .setBoolean(USER, flags.contains(Flag.USER))
            .setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()), String.class)
            .setInstant(INTERNAL_DATE, metadata.getInternalDate().get().toInstant())
            .setInt(BODY_START_OCTET, Math.toIntExact(metadata.getBodyStartOctet().get()))
            .setLong(FULL_CONTENT_OCTETS, metadata.getSize().get())
            .setString(HEADER_CONTENT, metadata.getHeaderContent().get().asString()));
    }

    public Mono<Boolean> updateMetadata(ComposedMessageId id, UpdatedFlags updatedFlags, ModSeq previousModeq) {
        return cassandraAsyncExecutor.executeReturnApplied(updateBoundStatement(id, updatedFlags, previousModeq));
    }

    private BoundStatement updateBoundStatement(ComposedMessageId id, UpdatedFlags updatedFlags, ModSeq previousModeq) {
        final BoundStatement boundStatement = update.bind()
            .setLong(MOD_SEQ, updatedFlags.getModSeq().asLong())
            .setUuid(MESSAGE_ID, ((CassandraMessageId) id.getMessageId()).get())
            .setUuid(MAILBOX_ID, ((CassandraId) id.getMailboxId()).asUuid())
            .setLong(IMAP_UID, id.getUid().asLong());

        MutableBoundStatementWrapper statementWrapper = new MutableBoundStatementWrapper(boundStatement);
        if (updatedFlags.isChanged(Flag.ANSWERED)) {
            statementWrapper.setNewStatement(statementWrapper.getStatement().setBoolean(ANSWERED, updatedFlags.isModifiedToSet(Flag.ANSWERED)));
        } else {
            statementWrapper.setNewStatement(statementWrapper.getStatement().unset(ANSWERED));
        }
        if (updatedFlags.isChanged(Flag.DRAFT)) {
            statementWrapper.setNewStatement(statementWrapper.getStatement().setBoolean(DRAFT, updatedFlags.isModifiedToSet(Flag.DRAFT)));
        } else {
            statementWrapper.setNewStatement(statementWrapper.getStatement().unset(DRAFT));
        }
        if (updatedFlags.isChanged(Flag.FLAGGED)) {
            statementWrapper.setNewStatement(statementWrapper.getStatement().setBoolean(FLAGGED, updatedFlags.isModifiedToSet(Flag.FLAGGED)));
        } else {
            statementWrapper.setNewStatement(statementWrapper.getStatement().unset(FLAGGED));
        }
        if (updatedFlags.isChanged(Flag.DELETED)) {
            statementWrapper.setNewStatement(statementWrapper.getStatement().setBoolean(DELETED, updatedFlags.isModifiedToSet(Flag.DELETED)));
        } else {
            statementWrapper.setNewStatement(statementWrapper.getStatement().unset(DELETED));
        }
        if (updatedFlags.isChanged(Flag.RECENT)) {
            statementWrapper.setNewStatement(statementWrapper.getStatement().setBoolean(RECENT, updatedFlags.getNewFlags().contains(Flag.RECENT)));
        } else {
            statementWrapper.setNewStatement(statementWrapper.getStatement().unset(RECENT));
        }
        if (updatedFlags.isChanged(Flag.SEEN)) {
            statementWrapper.setNewStatement(statementWrapper.getStatement().setBoolean(SEEN, updatedFlags.isModifiedToSet(Flag.SEEN)));
        } else {
            statementWrapper.setNewStatement(statementWrapper.getStatement().unset(SEEN));
        }
        if (updatedFlags.isChanged(Flag.USER)) {
            statementWrapper.setNewStatement(statementWrapper.getStatement().setBoolean(USER, updatedFlags.isModifiedToSet(Flag.USER)));
        } else {
            statementWrapper.setNewStatement(statementWrapper.getStatement().unset(USER));
        }
        Sets.SetView<String> removedFlags = Sets.difference(
            ImmutableSet.copyOf(updatedFlags.getOldFlags().getUserFlags()),
            ImmutableSet.copyOf(updatedFlags.getNewFlags().getUserFlags()));
        Sets.SetView<String> addedFlags = Sets.difference(
            ImmutableSet.copyOf(updatedFlags.getNewFlags().getUserFlags()),
            ImmutableSet.copyOf(updatedFlags.getOldFlags().getUserFlags()));
        if (addedFlags.isEmpty()) {
            statementWrapper.setNewStatement(statementWrapper.getStatement().unset(ADDED_USERS_FLAGS));
        } else {
            statementWrapper.setNewStatement(statementWrapper.getStatement().setSet(ADDED_USERS_FLAGS, addedFlags, String.class));
        }
        if (removedFlags.isEmpty()) {
            statementWrapper.setNewStatement(statementWrapper.getStatement().unset(REMOVED_USERS_FLAGS));
        } else {
            statementWrapper.setNewStatement(statementWrapper.getStatement().setSet(REMOVED_USERS_FLAGS, removedFlags, String.class));
        }
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            statementWrapper.setNewStatement(statementWrapper.getStatement().setLong(MOD_SEQ_CONDITION, previousModeq.asLong()));
            return statementWrapper.getStatement();
        }
        return statementWrapper.getStatement();
    }

    public Flux<CassandraMessageMetadata> retrieve(CassandraMessageId messageId, Optional<CassandraId> mailboxId, JamesExecutionProfiles.ConsistencyChoice readConsistencyChoice) {
        return cassandraAsyncExecutor.executeRows(setExecutionProfileIfNeeded(selectStatement(messageId, mailboxId), readConsistencyChoice))
            .map(this::toComposedMessageIdWithMetadata);
    }

    @VisibleForTesting
    public Flux<CassandraMessageMetadata> retrieve(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return retrieve(messageId, mailboxId, STRONG);
    }

    public Flux<CassandraMessageMetadata> retrieveAllMessages() {
        return cassandraAsyncExecutor.executeRows(listStatement.bind()
                .setTimeout(Duration.ofDays(1)))
            .map(this::toComposedMessageIdWithMetadata);
    }

    private CassandraMessageMetadata toComposedMessageIdWithMetadata(Row row) {
        final CassandraMessageId messageId = CassandraMessageId.Factory.of(row.getUuid(MESSAGE_ID_LOWERCASE));
        return CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(
                    CassandraId.of(row.getUuid(MAILBOX_ID_LOWERCASE)),
                    messageId,
                    MessageUid.of(row.getLong(IMAP_UID))))
                .flags(FlagsExtractor.getFlags(row))
                .threadId(getThreadIdFromRow(row, messageId))
                .modSeq(ModSeq.of(row.getLong(MOD_SEQ_LOWERCASE)))
                .build())
            .bodyStartOctet(row.getInt(BODY_START_OCTET_LOWERCASE))
            .internalDate(Date.from(row.getInstant(INTERNAL_DATE_LOWERCASE)))
            .size(row.getLong(FULL_CONTENT_OCTETS_LOWERCASE))
            .headerContent(Optional.ofNullable(row.getString(HEADER_CONTENT_LOWERCASE))
                .map(blobIdFactory::from))
            .build();
    }

    private ThreadId getThreadIdFromRow(Row row, MessageId messageId) {
        UUID threadIdUUID = row.getUuid(THREAD_ID_LOWERCASE);
        if (threadIdUUID == null) {
            return ThreadId.fromBaseMessageId(messageId);
        }
        return ThreadId.fromBaseMessageId(CassandraMessageId.Factory.of(threadIdUUID));
    }

    private BoundStatement selectStatement(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return mailboxId
            .map(cassandraId -> select.bind()
                .setUuid(MESSAGE_ID_LOWERCASE, messageId.get())
                .setUuid(MAILBOX_ID_LOWERCASE, cassandraId.asUuid()))
            .orElseGet(() -> selectAll.bind().setUuid(MESSAGE_ID_LOWERCASE, messageId.get()));
    }

    private BoundStatement setExecutionProfileIfNeeded(BoundStatement statement, JamesExecutionProfiles.ConsistencyChoice consistencyChoice) {
        if (consistencyChoice.equals(STRONG)) {
            return statement.setExecutionProfile(lwtProfile);
        } else {
            return statement;
        }
    }
}
