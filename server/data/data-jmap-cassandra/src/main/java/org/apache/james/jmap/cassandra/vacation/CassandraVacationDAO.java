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

package org.apache.james.jmap.cassandra.vacation;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.ZonedDateTimeCodec;
import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.cassandra.vacation.tables.CassandraVacationTable;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

public class CassandraVacationDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement readStatement;
    private final ZonedDateTimeCodec zonedDateTimeCodec;

    @Inject
    public CassandraVacationDAO(Session session, ZonedDateTimeCodec zonedDateTimeCodec) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.zonedDateTimeCodec = zonedDateTimeCodec;

        this.insertStatement = session.prepare(insertInto(CassandraVacationTable.TABLE_NAME)
            .value(CassandraVacationTable.ACCOUNT_ID, bindMarker(CassandraVacationTable.ACCOUNT_ID))
            .value(CassandraVacationTable.IS_ENABLED, bindMarker(CassandraVacationTable.IS_ENABLED))
            .value(CassandraVacationTable.FROM_DATE, bindMarker(CassandraVacationTable.FROM_DATE))
            .value(CassandraVacationTable.TO_DATE, bindMarker(CassandraVacationTable.TO_DATE))
            .value(CassandraVacationTable.TEXT, bindMarker(CassandraVacationTable.TEXT)));

        this.readStatement = session.prepare(select()
            .from(CassandraVacationTable.TABLE_NAME)
            .where(eq(CassandraVacationTable.ACCOUNT_ID,
                bindMarker(CassandraVacationTable.ACCOUNT_ID))));
    }

    public CompletableFuture<Void> modifyVacation(AccountId accountId, Vacation vacation) {
        return cassandraAsyncExecutor.executeVoid(
            insertStatement.bind()
                .setString(CassandraVacationTable.ACCOUNT_ID, accountId.getIdentifier())
                .setBool(CassandraVacationTable.IS_ENABLED, vacation.isEnabled())
                .set(CassandraVacationTable.FROM_DATE, vacation.getFromDate().orElse(null), zonedDateTimeCodec)
                .set(CassandraVacationTable.TO_DATE, vacation.getToDate().orElse(null), zonedDateTimeCodec)
                .setString(CassandraVacationTable.TEXT, vacation.getTextBody()));
    }

    public CompletableFuture<Optional<Vacation>> retrieveVacation(AccountId accountId) {
        return cassandraAsyncExecutor.executeSingleRow(readStatement.bind()
            .setString(CassandraVacationTable.ACCOUNT_ID, accountId.getIdentifier()))
            .thenApply(optional -> optional.map(row -> Vacation.builder()
                .enabled(row.getBool(CassandraVacationTable.IS_ENABLED))
                .fromDate(Optional.ofNullable(row.get(CassandraVacationTable.FROM_DATE, zonedDateTimeCodec)))
                .toDate(Optional.ofNullable(row.get(CassandraVacationTable.TO_DATE, zonedDateTimeCodec)))
                .textBody(row.getString(CassandraVacationTable.TEXT))
                .build()));
    }
}
