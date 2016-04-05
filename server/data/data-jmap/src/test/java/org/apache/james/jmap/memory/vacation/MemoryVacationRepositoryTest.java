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

package org.apache.james.jmap.memory.vacation;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.junit.Before;
import org.junit.Test;

public class MemoryVacationRepositoryTest {

    public static final AccountId ACCOUNT_ID = AccountId.create("identifier");
    public static final Vacation VACATION_ID_1 = Vacation.builder().enabled(true).build();
    public static final Vacation VACATION_ID_2 = Vacation.builder().enabled(true).build();
    private MemoryVacationRepository memoryVacationRepository;

    @Before
    public void setUp() {
        memoryVacationRepository = new MemoryVacationRepository();
    }

    @Test
    public void retrieveVacationShouldReturnDefaultValueByDefault() {
        assertThat(memoryVacationRepository.retrieveVacation(ACCOUNT_ID)).isEqualTo(VacationRepository.DEFAULT_VACATION);
    }

    @Test
    public void modifyVacationShouldWork() {
        memoryVacationRepository.modifyVacation(ACCOUNT_ID, VACATION_ID_1);

        assertThat(memoryVacationRepository.retrieveVacation(ACCOUNT_ID)).isEqualTo(VACATION_ID_1);
    }

    @Test
    public void modifyVacationShouldReplacePreviousValue() {
        memoryVacationRepository.modifyVacation(ACCOUNT_ID, VACATION_ID_1);
        memoryVacationRepository.modifyVacation(ACCOUNT_ID, VACATION_ID_2);

        assertThat(memoryVacationRepository.retrieveVacation(ACCOUNT_ID)).isEqualTo(VACATION_ID_2);
    }

    @Test(expected = NullPointerException.class)
    public void retrieveVacationShouldThrowOnNullAccountId() {
        memoryVacationRepository.retrieveVacation(null);
    }

    @Test(expected = NullPointerException.class)
    public void modifyVacationShouldThrowOnNullAccountId() {
        memoryVacationRepository.modifyVacation(null, VACATION_ID_1);
    }

    @Test(expected = NullPointerException.class)
    public void modifyVacationShouldThrowOnNullVacation() {
        memoryVacationRepository.modifyVacation(ACCOUNT_ID, null);
    }

}
