package org.apache.james.pop3server.mailbox.tombstone;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.SliceLookup.MAILBOX_ID;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.SliceLookup.MESSAGE_ID;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.SliceLookup.SLICE;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.SliceLookup.TABLE_NAME;

import java.util.Date;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

import reactor.core.publisher.Mono;

public class TimeSerieLookupDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement deleteAllStatement;
    private final PreparedStatement selectStatement;

    @Inject
    public TimeSerieLookupDAO(Session session) {
        executor = new CassandraAsyncExecutor(session);
        insertStatement = session.prepare(QueryBuilder.insertInto(TABLE_NAME)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(SLICE, bindMarker(SLICE)));

        deleteStatement = session.prepare(QueryBuilder.delete().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(QueryBuilder.eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));

        deleteAllStatement = session.prepare(QueryBuilder.delete().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));

        selectStatement = session.prepare(select().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(QueryBuilder.eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    public Mono<Void> save(CassandraId mailboxId, CassandraMessageId messageId, Slice slice) {
        return executor.executeVoid(insertStatement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid())
            .setUUID(MESSAGE_ID, messageId.get())
            .setTimestamp(SLICE, Date.from(slice.getStartSliceInstant())));
    }

    public Mono<Void> delete(CassandraId mailboxId, CassandraMessageId messageId) {
        return executor.executeVoid(deleteStatement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid())
            .setUUID(MESSAGE_ID, messageId.get()));
    }

    public Mono<Void> clear(CassandraId mailboxId) {
        return executor.executeVoid(deleteAllStatement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid()));
    }

    public Mono<Slice> retrieve(CassandraId mailboxId, CassandraMessageId messageId) {
        return executor.executeSingleRow(selectStatement.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setUUID(MESSAGE_ID, messageId.get()))
            .map(row -> Slice.of(row.getTimestamp(SLICE).toInstant()));
    }
}
