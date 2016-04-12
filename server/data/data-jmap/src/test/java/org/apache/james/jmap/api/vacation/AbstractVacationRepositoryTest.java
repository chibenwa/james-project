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

package org.apache.james.jmap.api.vacation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public abstract class AbstractVacationRepositoryTest {

    public static final AccountId ACCOUNT_ID = AccountId.create("identifier");
    public static final ZonedDateTime ZONED_DATE_TIME = ZonedDateTime.of(2016, 4, 3, 2, 1, 0, 0, ZoneId.systemDefault());
    public static final Vacation VACATION_1 = Vacation.builder().enabled(true).build();
    public static final Vacation VACATION_2 = Vacation.builder().fromDate(Optional.of(ZONED_DATE_TIME)).enabled(true).build();

    private VacationRepository vacationRepository;

    protected abstract VacationRepository createVacationRepository();

    @Before
    public void setUp() {
        vacationRepository = createVacationRepository();
    }

    @Test
    public void retrieveVacationShouldReturnDefaultValueByDefault() {
        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID)).isEqualTo(VacationRepository.DEFAULT_VACATION);
    }

    @Test
    public void modifyVacationShouldWork() {
        vacationRepository.modifyVacation(ACCOUNT_ID, VACATION_1);

        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID)).isEqualTo(VACATION_1);
    }

    @Test
    public void modifyVacationShouldReplacePreviousValue() {
        vacationRepository.modifyVacation(ACCOUNT_ID, VACATION_1);
        vacationRepository.modifyVacation(ACCOUNT_ID, VACATION_2);

        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID)).isEqualTo(VACATION_2);
    }

    @Test(expected = NullPointerException.class)
    public void retrieveVacationShouldThrowOnNullAccountId() {
        vacationRepository.retrieveVacation(null);
    }

    @Test(expected = NullPointerException.class)
    public void modifyVacationShouldThrowOnNullAccountId() {
        vacationRepository.modifyVacation(null, VACATION_1);
    }

    @Test(expected = NullPointerException.class)
    public void modifyVacationShouldThrowOnNullVacation() {
        vacationRepository.modifyVacation(ACCOUNT_ID, null);
    }

}
