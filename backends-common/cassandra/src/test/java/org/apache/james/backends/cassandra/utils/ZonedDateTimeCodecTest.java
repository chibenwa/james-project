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

package org.apache.james.backends.cassandra.utils;

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.components.CassandraIndex;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableList;

@RunWith(Parameterized.class)
public class ZonedDateTimeCodecTest {

    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
        return Lists.newArrayList(new Object[] {null},
            new Object[] {ZonedDateTime.parse("2016-04-07T02:01Z")},
            new Object[] {ZonedDateTime.parse("2016-04-07T02:01:05+07:00")},
            new Object[] {ZonedDateTime.parse("2016-04-07T02:01:05+07:00[Asia/Vientiane]")});
    }

    private final ZonedDateTime zonedDateTime;

    private ZonedDateTimeCodec zonedDateTimeCodec;
    private CassandraCluster cassandra;

    public ZonedDateTimeCodecTest(ZonedDateTime zonedDateTime) {
        this.zonedDateTime = zonedDateTime;
    }

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(
            new CassandraModuleComposite(
                new CassandraZonedDateTimeModule(),
                new DateRowModule()));

        zonedDateTimeCodec = new ZonedDateTimeCodec(cassandra.getTypesProvider().getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME));
        cassandra.getConf()
            .getCluster()
            .getConfiguration()
            .getCodecRegistry()
            .register(zonedDateTimeCodec);
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
    }

    @Test
    public void zonedDateTimeShouldBeRetrieved() {
        cassandra.getConf().execute(insertInto(DateRowModule.TABLE_NAME)
            .value(DateRowModule.KEY, 18)
            .value(DateRowModule.DATE, zonedDateTime));
        ZonedDateTime retrievedZonedDateTime = cassandra.getConf()
            .execute(select()
                .from(DateRowModule.TABLE_NAME)
                .where(eq(DateRowModule.KEY, 18)))
            .one()
            .get(DateRowModule.DATE, zonedDateTimeCodec);
        if (zonedDateTime == null) {
            assertThat(retrievedZonedDateTime).isNull();
        } else {
            assertThat(retrievedZonedDateTime).isEqualTo(zonedDateTime);
        }
    }

    static class DateRowModule implements CassandraModule {
        public static final String TABLE_NAME = "date_row_table";
        public static final String KEY = "key";
        public static final String DATE = "date";

        @Override
        public List<CassandraTable> moduleTables() {
            return ImmutableList.of(new CassandraTable(TABLE_NAME,
                SchemaBuilder.createTable(TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(KEY, bigint())
                    .addUDTColumn(DATE, SchemaBuilder.frozen(CassandraZonedDateTimeModule.ZONED_DATE_TIME))));
        }

        @Override
        public List<CassandraIndex> moduleIndex() {
            return Collections.emptyList();
        }

        @Override
        public List<CassandraType> moduleTypes() {
            return Collections.emptyList();
        }
    }

}
