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

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.Test;

public class VacationTest {

    public static final ZonedDateTime DATE_TIME_1 = ZonedDateTime.parse("2016-10-09T08:07:06+07:00[Asia/Vientiane]");
    public static final ZonedDateTime DATE_TIME_2 = ZonedDateTime.parse("2017-10-09T08:07:06+07:00[Asia/Vientiane]");
    public static final ZonedDateTime DATE_TIME_3 = ZonedDateTime.parse("2018-10-09T08:07:06+07:00[Asia/Vientiane]");

    @Test
    public void disabledVacationsAreNotActive() {
        assertThat(
            Vacation.builder()
                .enabled(false)
                .build()
                .isActiveAtDate(DATE_TIME_1))
            .isFalse();
    }

    @Test
    public void enabledVacationWithoutDatesIsActive() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .build()
                .isActiveAtDate(DATE_TIME_1))
            .isTrue();
    }

    @Test
    public void rangeShouldBeInclusive() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .fromDate(Optional.of(DATE_TIME_1))
                .toDate(Optional.of(DATE_TIME_1))
                .build()
                .isActiveAtDate(DATE_TIME_1))
            .isTrue();
    }

    @Test
    public void rangeShouldWork() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .fromDate(Optional.of(DATE_TIME_1))
                .toDate(Optional.of(DATE_TIME_3))
                .build()
                .isActiveAtDate(DATE_TIME_2))
            .isTrue();
    }

    @Test(expected = NullPointerException.class)
    public void isActiveAtDateShouldThrowOnNullValue() {
        Vacation.builder()
            .enabled(true)
            .fromDate(Optional.of(DATE_TIME_1))
            .toDate(Optional.of(DATE_TIME_1))
            .build()
            .isActiveAtDate(null);
    }

    @Test
    public void fromShouldWork() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .fromDate(Optional.of(DATE_TIME_1))
                .build()
                .isActiveAtDate(DATE_TIME_2))
            .isTrue();
    }

    @Test
    public void vacationShouldNotBeActiveBeforeFromDate() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .fromDate(Optional.of(DATE_TIME_2))
                .build()
                .isActiveAtDate(DATE_TIME_1))
            .isFalse();
    }

    @Test
    public void vacationShouldNotBeActiveAfterToDate() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .toDate(Optional.of(DATE_TIME_2))
                .build()
                .isActiveAtDate(DATE_TIME_3))
            .isFalse();
    }

    @Test
    public void vacationShouldBeActiveBeforeToDate() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .toDate(Optional.of(DATE_TIME_2))
                .build()
                .isActiveAtDate(DATE_TIME_1))
            .isTrue();
    }

}
