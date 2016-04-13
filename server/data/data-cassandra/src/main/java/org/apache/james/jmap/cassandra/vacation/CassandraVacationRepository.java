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

import java.time.ZonedDateTime;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.utils.ZonedDateTimeRepresentation;
import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.cassandra.vacation.tables.CassandraVacationTable;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.UserType;

public class CassandraVacationRepository implements VacationRepository {

    private final Session session;
    private final PreparedStatement insertStatement;
    private final PreparedStatement readStatement;
    private final UserType zonedDateTimeUserType;

    @Inject
    public CassandraVacationRepository(Session session, CassandraTypesProvider cassandraTypesProvider) {
        this.session = session;
        this.zonedDateTimeUserType = cassandraTypesProvider.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME);
        this.insertStatement = this.session.prepare(insertInto(CassandraVacationTable.TABLE_NAME)
            .value(CassandraVacationTable.ACCOUNT_ID, bindMarker(CassandraVacationTable.ACCOUNT_ID))
            .value(CassandraVacationTable.IS_ENABLED, bindMarker(CassandraVacationTable.IS_ENABLED))
            .value(CassandraVacationTable.FROM_DATE, bindMarker(CassandraVacationTable.FROM_DATE))
            .value(CassandraVacationTable.TO_DATE, bindMarker(CassandraVacationTable.TO_DATE))
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
            .setUDTValue(CassandraVacationTable.FROM_DATE, convertToUDTValue(vacation.getFromDate()))
            .setUDTValue(CassandraVacationTable.TO_DATE, convertToUDTValue(vacation.getToDate()))
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
                .fromDate(retrieveDate(row, CassandraVacationTable.FROM_DATE))
                .toDate(retrieveDate(row, CassandraVacationTable.TO_DATE))
                .textBody(row.getString(CassandraVacationTable.TEXT))
                .build())
            .orElse(VacationRepository.DEFAULT_VACATION);
    }

    private Optional<ZonedDateTime> retrieveDate(Row row, String dateField) {
        return Optional.ofNullable(row.getUDTValue(dateField))
            .map(udtValue -> new ZonedDateTimeRepresentation(
                udtValue.getLong(CassandraZonedDateTimeModule.MS_SINCE_EPOCH),
                udtValue.getString(CassandraZonedDateTimeModule.TIME_ZONE))
                .convertToZonedDateTime());
    }

    private UDTValue convertToUDTValue(Optional<ZonedDateTime> zonedDateTimeOptional) {
        return zonedDateTimeOptional.map(ZonedDateTimeRepresentation::fromZonedDateTime)
            .map(representation -> zonedDateTimeUserType.newValue()
                .setLong(CassandraZonedDateTimeModule.MS_SINCE_EPOCH, representation.getMsSinceEpoch())
                .setString(CassandraZonedDateTimeModule.TIME_ZONE, representation.getSerializedZoneId()))
            .orElse(null);
    }
}
