package org.apache.james.pop3server.mailbox.tombstone;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.StartingSlice.MAILBOX_ID;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.StartingSlice.STARTING_SLICE;
import static org.apache.james.pop3server.mailbox.tombstone.Pop3TimeSerieMetadataModule.StartingSlice.TABLE_NAME;

import java.util.Date;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

import reactor.core.publisher.Mono;

public class TimeSerieStartDAO {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;
    private final PreparedStatement deleteStatement;

    @Inject
    public TimeSerieStartDAO(Session session) {
        executor = new CassandraAsyncExecutor(session);
        insertStatement = session.prepare(QueryBuilder.insertInto(TABLE_NAME)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(STARTING_SLICE, bindMarker(STARTING_SLICE)));

        selectStatement = session.prepare(select().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));

        deleteStatement = session.prepare(delete().from(TABLE_NAME)
            .where(QueryBuilder.eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    public Mono<Void> save(CassandraId mailboxId, Slice startSlice) {
        return executor.executeVoid(insertStatement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid())
            .setTimestamp(STARTING_SLICE, Date.from(startSlice.getStartSliceInstant())));
    }

    public Mono<Void> clear(CassandraId mailboxId) {
        return executor.executeVoid(deleteStatement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid()));
    }

    public Mono<Slice> retrieve(CassandraId mailboxId) {
        return executor.executeSingleRow(selectStatement.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .map(row -> Slice.of(row.getTimestamp(STARTING_SLICE).toInstant()));
    }
}
