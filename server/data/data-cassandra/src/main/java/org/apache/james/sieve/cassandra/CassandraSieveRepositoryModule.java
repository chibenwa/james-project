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

package org.apache.james.sieve.cassandra;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableList;
import org.apache.james.backends.cassandra.components.CassandraIndex;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.sieve.cassandra.tables.*;

import java.util.List;

import static com.datastax.driver.core.DataType.*;

public class CassandraSieveRepositoryModule implements CassandraModule {

    private final List<CassandraTable> tables;
    private final List<CassandraIndex> index;
    private final List<CassandraType> types;

    public CassandraSieveRepositoryModule() {
        tables = ImmutableList.of(
                new CassandraTable(CassandraSieveTable.TABLE_NAME,
                        SchemaBuilder.createTable(CassandraSieveTable.TABLE_NAME)
                                .ifNotExists()
                                .addPartitionKey(CassandraSieveTable.USER_NAME, text())
                                .addClusteringColumn(CassandraSieveTable.SCRIPT_NAME, text())
                                .addColumn(CassandraSieveTable.SCRIPT_CONTENT, text())
                ),
                new CassandraTable(CassandraSieveActiveTable.TABLE_NAME,
                        SchemaBuilder.createTable(CassandraSieveActiveTable.TABLE_NAME)
                                .ifNotExists()
                                .addPartitionKey(CassandraSieveActiveTable.USER_NAME, text())
                                .addColumn(CassandraSieveActiveTable.SCRIPT_NAME, text())
                                .addColumn(CassandraSieveActiveTable.SCRIPT_CONTENT, text())
                ),
                new CassandraTable(CassandraSieveSpaceTable.TABLE_NAME,
                        SchemaBuilder.createTable(CassandraSieveSpaceTable.TABLE_NAME)
                                .ifNotExists()
                                .addPartitionKey(CassandraSieveSpaceTable.USER_NAME, text())
                                .addColumn(CassandraSieveSpaceTable.SPACE_USED, counter())
                ),
                new CassandraTable(CassandraSieveQuotaTable.TABLE_NAME,
                        SchemaBuilder.createTable(CassandraSieveQuotaTable.TABLE_NAME)
                                .ifNotExists()
                                .addPartitionKey(CassandraSieveQuotaTable.USER_NAME, text())
                                .addColumn(CassandraSieveQuotaTable.QUOTA, bigint())
                ),
                new CassandraTable(CassandraSieveClusterQuotaTable.TABLE_NAME,
                        SchemaBuilder.createTable(CassandraSieveClusterQuotaTable.TABLE_NAME)
                                .ifNotExists()
                                .addPartitionKey(CassandraSieveClusterQuotaTable.NAME, text())
                                .addColumn(CassandraSieveClusterQuotaTable.VALUE, bigint())
                )
        );
        index = ImmutableList.of();
        types = ImmutableList.of();
    }

    @Override
    public List<CassandraTable> moduleTables() {
        return tables;
    }

    @Override
    public List<CassandraIndex> moduleIndex() {
        return index;
    }

    @Override
    public List<CassandraType> moduleTypes() {
        return types;
    }
}
