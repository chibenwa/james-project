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

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.NotificationRegistry;
import org.apache.james.jmap.api.vacation.RecipientId;
import org.apache.james.util.date.ZonedDateTimeProvider;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

public class CassandraNotificationRegistry implements NotificationRegistry {

    private final ZonedDateTimeProvider zonedDateTimeProvider;
    private final CassandraNotificationRegistryDAO cassandraNotificationRegistryDAO;

    @Inject
    public CassandraNotificationRegistry(ZonedDateTimeProvider zonedDateTimeProvider, CassandraNotificationRegistryDAO cassandraNotificationRegistryDAO) {
        this.zonedDateTimeProvider = zonedDateTimeProvider;
        this.cassandraNotificationRegistryDAO = cassandraNotificationRegistryDAO;
    }

    @Override
    public CompletableFuture<Void> register(AccountId accountId, RecipientId recipientId, Optional<ZonedDateTime> expiryDate) {
        return cassandraNotificationRegistryDAO.register(accountId,
            recipientId,
            expiryDate.map(expiry -> Ints.checkedCast(zonedDateTimeProvider.get().until(expiry, ChronoUnit.SECONDS)))
                .map(this::throwOnNegativeOrNullValue));
    }

    @Override
    public CompletableFuture<Boolean> isRegistered(AccountId accountId, RecipientId recipientId) {
        return cassandraNotificationRegistryDAO.isRegistered(accountId, recipientId);
    }

    @Override
    public CompletableFuture<Void> flush(AccountId accountId) {
        return cassandraNotificationRegistryDAO.flush(accountId);
    }

    public int throwOnNegativeOrNullValue(int value) {
        Preconditions.checkArgument(value > 0);
        return value;
    }
}
