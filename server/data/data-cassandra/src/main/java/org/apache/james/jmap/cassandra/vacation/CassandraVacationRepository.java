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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.cassandra.vacation.tables.CassandraVacationTable;
import org.apache.james.util.date.ZonedDateTimeConverter;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

public class CassandraVacationRepository implements VacationRepository {

    private final Session session;
    private final PreparedStatement insertStatement;
    private final PreparedStatement readStatement;

    @Inject
    public CassandraVacationRepository(Session session) {
        this.session = session;
        this.insertStatement = this.session.prepare(insertInto(CassandraVacationTable.TABLE_NAME)
            .value(CassandraVacationTable.ACCOUNT_ID, bindMarker(CassandraVacationTable.ACCOUNT_ID))
            .value(CassandraVacationTable.IS_ENABLED, bindMarker(CassandraVacationTable.IS_ENABLED))
            .value(CassandraVacationTable.FROM_DATE, bindMarker(CassandraVacationTable.FROM_DATE))
            .value(CassandraVacationTable.FROM_TIMEZONE, bindMarker(CassandraVacationTable.FROM_TIMEZONE))
            .value(CassandraVacationTable.TO_DATE, bindMarker(CassandraVacationTable.TO_DATE))
            .value(CassandraVacationTable.TO_TIMEZONE, bindMarker(CassandraVacationTable.TO_TIMEZONE))
            .value(CassandraVacationTable.TEXT, bindMarker(CassandraVacationTable.TEXT)));
        this.readStatement = this.session.prepare(select()
            .from(CassandraVacationTable.TABLE_NAME)
            .where(eq(CassandraVacationTable.ACCOUNT_ID,
                bindMarker(CassandraVacationTable.ACCOUNT_ID))));
    }

    @Override
    public void modifyVacation(AccountId accountId, Vacation vacation) {
        session.execute(insertStatement.bind()
            .setString(CassandraVacationTable.ACCOUNT_ID, accountId.getIdentifier())
            .setBool(CassandraVacationTable.IS_ENABLED, vacation.isEnabled())
            .setDate(CassandraVacationTable.FROM_DATE, ZonedDateTimeConverter.toDate(vacation.getFromDate()).orElse(null))
            .setDate(CassandraVacationTable.TO_DATE, ZonedDateTimeConverter.toDate(vacation.getToDate()).orElse(null))
            .setString(CassandraVacationTable.FROM_TIMEZONE, extractTimeZone(vacation.getFromDate()))
            .setString(CassandraVacationTable.TO_TIMEZONE, extractTimeZone(vacation.getToDate()))
            .setString(CassandraVacationTable.TEXT, vacation.getTextBody()));
    }

    @Override
    public Vacation retrieveVacation(AccountId accountId) {
        return Optional.ofNullable(
            session.execute(
                readStatement.bind()
                    .setString(CassandraVacationTable.ACCOUNT_ID, accountId.getIdentifier()))
                .one())
            .map(row -> Vacation.builder()
                .enabled(row.getBool(CassandraVacationTable.IS_ENABLED))
                .fromDate(retrieveDate(row, CassandraVacationTable.FROM_DATE, CassandraVacationTable.FROM_TIMEZONE))
                .toDate(retrieveDate(row, CassandraVacationTable.TO_DATE, CassandraVacationTable.TO_TIMEZONE))
                .textBody(row.getString(CassandraVacationTable.TEXT))
                .build())
            .orElse(VacationRepository.DEFAULT_VACATION);
    }

    private Optional<ZonedDateTime> retrieveDate(Row row, String dateField, String timeZoneField) {
        return ZonedDateTimeConverter.toZonedDateTime(
            Optional.ofNullable(row.getDate(dateField)),
            Optional.ofNullable(row.getString(timeZoneField)).map(ZoneId::of));
    }

    private String extractTimeZone(Optional<ZonedDateTime> zonedDateTime) {
        return zonedDateTime.map(ZonedDateTime::getZone).map(ZoneId::getId).orElse(null);
    }
}
