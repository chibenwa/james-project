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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;

public class VacationResponseTest {

    public static final String IDENTIFIER = "identifier";
    public static final String MESSAGE = "A message explaining I am in vacation";
    public static final ZonedDateTime FROM_DATE = ZonedDateTime.of(2016, 4, 6, 18, 43, 36, 0, ZoneId.systemDefault());
    public static final ZonedDateTime TO_DATE = ZonedDateTime.of(2016, 4, 21, 18, 43, 36, 0, ZoneId.systemDefault());

    @Test
    public void vacationResponseBuilderShouldWork() {
        VacationResponse vacationResponse = VacationResponse.builder()
            .id(IDENTIFIER)
            .enabled(true)
            .fromDate(FROM_DATE)
            .toDate(TO_DATE)
            .textBody(MESSAGE)
            .build();

        assertThat(vacationResponse.getId()).isEqualTo(IDENTIFIER);
        assertThat(vacationResponse.isEnabled()).isEqualTo(true);
        assertThat(vacationResponse.getTextBody()).isEqualTo(MESSAGE);
        assertThat(vacationResponse.getFromDate()).isEqualTo(FROM_DATE);
        assertThat(vacationResponse.getToDate()).isEqualTo(TO_DATE);
    }

}
