package org.apache.james.pop3server.mailbox.tombstone;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.TimeSerie.MAILBOX_ID;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.TimeSerie.MESSAGE_ID;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.TimeSerie.SIZE;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.TimeSerie.SLICE;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.TimeSerie.TABLE_NAME;

import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.pop3server.mailbox.Pop3MetadataStore.FullMetadata;
import org.apache.james.pop3server.mailbox.Pop3MetadataStore.StatMetadata;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Pop3TimeSerieDAO {

    private static final String START = "start";
    private static final String BEFORE = "before";

    public static class TimeSerieStatMetadata {
        private final Instant instant;
        private final StatMetadata metadata;
        private final MailboxId mailboxId;

        public TimeSerieStatMetadata(Instant instant, StatMetadata metadata, MailboxId mailboxId) {
            this.instant = instant;
            this.metadata = metadata;
            this.mailboxId = mailboxId;
        }

        public Instant getInstant() {
            return instant;
        }

        public StatMetadata getMetadata() {
            return metadata;
        }

        public FullMetadata getFullMetadata() {
            return new FullMetadata(mailboxId, metadata);
        }
    }

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement deleteAllStatement;
    private final PreparedStatement selectStatement;
    private final PreparedStatement selectFromStatement;
    private final PreparedStatement selectMailboxStatement;
    private final PreparedStatement selectAllStatement;
    private final PreparedStatement readBeforeStatement;
    private final PreparedStatement readBetweenStatement;

    @Inject
    public Pop3TimeSerieDAO(Session session) {
        executor = new CassandraAsyncExecutor(session);
        insertStatement = session.prepare(QueryBuilder.insertInto(TABLE_NAME)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(SLICE, bindMarker(SLICE))
            .value(SIZE, bindMarker(SIZE)));

        deleteStatement = session.prepare(QueryBuilder.delete().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(QueryBuilder.eq(MESSAGE_ID, bindMarker(MESSAGE_ID)))
            .and(QueryBuilder.eq(SLICE, bindMarker(SLICE))));

        deleteAllStatement = session.prepare(QueryBuilder.delete().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));

        selectStatement = session.prepare(select().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(QueryBuilder.eq(MESSAGE_ID, bindMarker(MESSAGE_ID)))
            .and(QueryBuilder.eq(SLICE, bindMarker(SLICE))));

        selectFromStatement = session.prepare(select().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(QueryBuilder.gte(SLICE, bindMarker(SLICE))));

        selectMailboxStatement = session.prepare(select().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));

        selectAllStatement = session.prepare(select().from(TABLE_NAME));

        readBeforeStatement = session.prepare(select().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(QueryBuilder.lt(SLICE, bindMarker(BEFORE))));

        readBetweenStatement = session.prepare(select().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(QueryBuilder.lt(SLICE, bindMarker(BEFORE)))
            .and(QueryBuilder.gte(SLICE, bindMarker(START))));
    }

    public Mono<Void> save(CassandraId mailboxId, Slice slice, StatMetadata statMetadata) {
        return executor.executeVoid(insertStatement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid())
            .setUUID(MESSAGE_ID, ((CassandraMessageId) statMetadata.getMessageId()).get())
            .setLong(SIZE, statMetadata.getSize())
            .setTimestamp(SLICE, Date.from(slice.getStartSliceInstant())));
    }

    public Mono<Void> delete(CassandraId mailboxId, Slice slice, CassandraMessageId messageId) {
        return executor.executeVoid(deleteStatement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid())
            .setUUID(MESSAGE_ID, messageId.get())
            .setTimestamp(SLICE, Date.from(slice.getStartSliceInstant())));
    }

    public Mono<FullMetadata> retrieve(CassandraId mailboxId, Slice slice, CassandraMessageId messageId) {
        return executor.executeSingleRow(selectStatement.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setUUID(MESSAGE_ID, messageId.get())
                .setTimestamp(SLICE, Date.from(slice.getStartSliceInstant())))
            .map(rowToFullMetadataFunction());
    }

    public Flux<TimeSerieStatMetadata> readBefore(CassandraId mailboxId, Date before) {
        return executor.executeRows(
                readBeforeStatement.bind()
                    .setUUID(MAILBOX_ID, mailboxId.asUuid())
                    .setTimestamp(BEFORE, before))
            .map(row -> fromRow(mailboxId, row));
    }

    public Flux<TimeSerieStatMetadata> stat(CassandraId mailboxId, Slice slice) {
        return executor.executeRows(selectFromStatement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid())
            .setTimestamp(SLICE, Date.from(slice.getStartSliceInstant())))
            .map(row -> fromRow(mailboxId, row));
    }

    public Flux<TimeSerieStatMetadata> readBefore(CassandraId mailboxId, Date before, Date start) {
        return executor.executeRows(readBetweenStatement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid())
            .setTimestamp(BEFORE, before)
            .setTimestamp(START, start))
            .map(row -> fromRow(mailboxId, row));
    }

    public Flux<TimeSerieStatMetadata> statMailbox(CassandraId mailboxId) {
        return executor.executeRows(selectMailboxStatement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .map(row -> fromRow(mailboxId, row));
    }

    private TimeSerieStatMetadata fromRow(MailboxId mailboxId, Row row) {
        return new TimeSerieStatMetadata(
            row.getTimestamp(SLICE).toInstant(),
            new StatMetadata(
                CassandraMessageId.Factory.of(row.getUUID(MESSAGE_ID)),
                row.getLong(SIZE)), mailboxId);
    }

    public Flux<FullMetadata> listAll() {
        return executor.executeRows(selectAllStatement.bind())
            .map(row -> new FullMetadata(
                CassandraId.of(row.getUUID(MAILBOX_ID)),
                CassandraMessageId.Factory.of(row.getUUID(MESSAGE_ID)),
                row.getLong(SIZE)));
    }

    public Mono<Void> clear(CassandraId mailboxId) {
        return executor.executeVoid(deleteAllStatement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid()));
    }

    private Function<Row, FullMetadata> rowToFullMetadataFunction() {
        return row -> new FullMetadata(
            CassandraId.of(row.getUUID(MAILBOX_ID)),
            CassandraMessageId.Factory.of(row.getUUID(MESSAGE_ID)),
            row.getLong(SIZE));
    }
}
