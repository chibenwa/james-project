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

package org.apache.james.vacation.postgres;

import static org.apache.james.vacation.postgres.PostgresVacationDataDefinition.PostgresVacationNotificationRegistryTable.ACCOUNT_ID;
import static org.apache.james.vacation.postgres.PostgresVacationDataDefinition.PostgresVacationNotificationRegistryTable.EXPIRY_DATE;
import static org.apache.james.vacation.postgres.PostgresVacationDataDefinition.PostgresVacationNotificationRegistryTable.RECIPIENT_ID;
import static org.apache.james.vacation.postgres.PostgresVacationDataDefinition.PostgresVacationNotificationRegistryTable.TABLE_NAME;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.backends.postgres.PostgresCommons;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.RecipientId;
import org.jooq.Field;

import reactor.core.publisher.Mono;

public class PostgresNotificationRegistryDAO {
    // Qualified, so that ON CONFLICT DO UPDATE reads the existing row rather than the excluded one
    private static final Field<LocalDateTime> STORED_EXPIRY_DATE = PostgresCommons.tableField(TABLE_NAME, EXPIRY_DATE);

    private final PostgresExecutor postgresExecutor;
    private final ZonedDateTimeProvider zonedDateTimeProvider;

    public PostgresNotificationRegistryDAO(PostgresExecutor postgresExecutor,
                                           ZonedDateTimeProvider zonedDateTimeProvider) {
        this.postgresExecutor = postgresExecutor;
        this.zonedDateTimeProvider = zonedDateTimeProvider;
    }

    public Mono<Void> register(AccountId accountId, RecipientId recipientId, Optional<ZonedDateTime> expiryDate) {
        LocalDateTime utcExpiryDate = toUTC(expiryDate);

        return deleteExpired(accountId)
            .then(postgresExecutor.executeVoid(dsl -> Mono.from(dsl.insertInto(TABLE_NAME)
                .set(ACCOUNT_ID, accountId.getIdentifier())
                .set(RECIPIENT_ID, recipientId.getAsString())
                .set(EXPIRY_DATE, utcExpiryDate)
                .onConflict(ACCOUNT_ID, RECIPIENT_ID)
                .doUpdate()
                .set(EXPIRY_DATE, utcExpiryDate))));
    }

    // Only an expired registration is replaced, within the insert, so that concurrent callers cannot both register
    public Mono<Boolean> registerIfAbsent(AccountId accountId, RecipientId recipientId, Optional<ZonedDateTime> expiryDate) {
        LocalDateTime utcExpiryDate = toUTC(expiryDate);
        LocalDateTime currentUTCTime = currentUTCTime();

        return deleteExpired(accountId)
            .then(postgresExecutor.executeReturnAffectedRowsCount(dsl -> Mono.from(dsl.insertInto(TABLE_NAME)
                .set(ACCOUNT_ID, accountId.getIdentifier())
                .set(RECIPIENT_ID, recipientId.getAsString())
                .set(EXPIRY_DATE, utcExpiryDate)
                .onConflict(ACCOUNT_ID, RECIPIENT_ID)
                .doUpdate()
                .set(EXPIRY_DATE, utcExpiryDate)
                .where(STORED_EXPIRY_DATE.lt(currentUTCTime)))))
            .map(affectedRows -> affectedRows > 0);
    }

    public Mono<Void> unregister(AccountId accountId, RecipientId recipientId) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(ACCOUNT_ID.eq(accountId.getIdentifier()),
                RECIPIENT_ID.eq(recipientId.getAsString()))));
    }

    private LocalDateTime toUTC(Optional<ZonedDateTime> date) {
        return date.map(zonedDateTime -> zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
            .orElse(null);
    }

    private LocalDateTime currentUTCTime() {
        return zonedDateTimeProvider.get().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    // Nothing else removes them: without this, every sender ever answered would stay until a flush
    private Mono<Void> deleteExpired(AccountId accountId) {
        LocalDateTime currentUTCTime = currentUTCTime();

        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(ACCOUNT_ID.eq(accountId.getIdentifier()),
                EXPIRY_DATE.lt(currentUTCTime))));
    }

    public Mono<Boolean> isRegistered(AccountId accountId, RecipientId recipientId) {
        LocalDateTime currentUTCTime = currentUTCTime();

        return postgresExecutor.executeExists(dsl -> dsl.selectOne()
            .from(TABLE_NAME)
            .where(ACCOUNT_ID.eq(accountId.getIdentifier()),
                RECIPIENT_ID.eq(recipientId.getAsString()),
                EXPIRY_DATE.ge(currentUTCTime).or(EXPIRY_DATE.isNull())));
    }

    public Mono<Void> flush(AccountId accountId) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(ACCOUNT_ID.eq(accountId.getIdentifier()))));
    }
}
