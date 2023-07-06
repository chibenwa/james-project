package org.apache.james.pop3server.mailbox.tombstone;

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.timestamp;
import static com.datastax.driver.core.DataType.uuid;
import static com.datastax.driver.core.schemabuilder.SchemaBuilder.Direction.ASC;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface Pop3TimeSerieMetadataModule {

    interface SliceLookup {
        String TABLE_NAME = "pop3_timeserie_slice_lookup";
        String MAILBOX_ID = "mailbox_id";
        String MESSAGE_ID = "message_id";
        String SLICE = "slice";
    }

    interface TimeSerie {
        String TABLE_NAME = "pop3_timeserie";
        String MAILBOX_ID = "mailbox_id";
        String MESSAGE_ID = "message_id";
        String SLICE = "slice";
        String SIZE = "size";
    }

    interface StartingSlice {
        String TABLE_NAME = "pop3_timeserie_starting_slice";
        String MAILBOX_ID = "mailbox_id";
        String STARTING_SLICE = "starting_slice";
    }

    CassandraModule MODULE = CassandraModule.table(SliceLookup.TABLE_NAME)
        .comment("Store slice for each message in order to remove entries off the time series")
        .options(options -> options
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(SliceLookup.MAILBOX_ID, uuid())
            .addClusteringColumn(SliceLookup.MESSAGE_ID, uuid())
            .addColumn(SliceLookup.SLICE, timestamp()))

        .table(TimeSerie.TABLE_NAME)
        .comment("Store messages organised by slices in order to limit encountered tombstones")
        .options(options -> options
            .clusteringOrder(TimeSerie.SLICE, ASC)
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(TimeSerie.MAILBOX_ID, uuid())
            .addClusteringColumn(TimeSerie.SLICE, timestamp())
            .addClusteringColumn(TimeSerie.MESSAGE_ID, uuid())
            .addColumn(TimeSerie.SIZE, bigint()))

        .table(StartingSlice.TABLE_NAME)
        .comment("Store startingSlice for each mailbox.")
        .options(options -> options
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(StartingSlice.MAILBOX_ID, uuid())
            .addColumn(StartingSlice.STARTING_SLICE, timestamp()))

        .build();

}
