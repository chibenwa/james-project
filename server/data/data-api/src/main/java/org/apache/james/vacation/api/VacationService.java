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

package org.apache.james.vacation.api;

import java.time.ZonedDateTime;
import java.util.Optional;

import reactor.core.publisher.Mono;

public interface VacationService {

    Mono<Void> modifyVacation(AccountId accountId, VacationPatch vacationPatch);

    Mono<Vacation> retrieveVacation(AccountId accountId);

    Mono<Void> registerNotification(AccountId accountId, RecipientId recipientId, Optional<ZonedDateTime> expiryDate);

    /**
     * @return true when this call registered the notification, false when it was already registered
     * @see NotificationRegistry#registerIfAbsent(AccountId, RecipientId, Optional)
     */
    Mono<Boolean> registerNotificationIfAbsent(AccountId accountId, RecipientId recipientId, Optional<ZonedDateTime> expiryDate);

    Mono<Boolean> isNotificationRegistered(AccountId accountId, RecipientId recipientId);

    Mono<Void> unregisterNotification(AccountId accountId, RecipientId recipientId);

    Mono<Void> flushNotifications(AccountId accountId);

}
