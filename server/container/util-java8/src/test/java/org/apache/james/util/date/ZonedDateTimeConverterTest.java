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

package org.apache.james.util.date;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.Test;

public class ZonedDateTimeConverterTest {

    public static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    public static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    public static final ZoneId OTHER_ZONE = ZoneId.of("GMT+2");
    public static final ZonedDateTime ZONED_DATE_TIME = ZonedDateTime.of(2016, 4, 3, 2, 1, 0, 0, DEFAULT_ZONE);
    public static final ZonedDateTime ZONED_DATE_TIME_OTHER_ZONE = ZONED_DATE_TIME.withZoneSameInstant(OTHER_ZONE);

    public static final String DATE_STRING = "2016-04-03 02:01:00";

    @Test
    public void toDateShouldWork() throws Exception {
        assertThat(ZonedDateTimeConverter.toDate(Optional.of(ZONED_DATE_TIME))).isEqualTo(Optional.of(FORMATTER.parse(DATE_STRING)));
    }

    @Test
    public void toZoneDateTimeShouldWork() throws Exception {
        assertThat(ZonedDateTimeConverter.toZonedDateTime(
            Optional.of(FORMATTER.parse(DATE_STRING)),
            Optional.of(DEFAULT_ZONE)))
            .isEqualTo(Optional.of(ZONED_DATE_TIME));
    }

    @Test
    public void toDateShouldWorkWithNullValue() throws Exception {
        assertThat(ZonedDateTimeConverter.toDate(Optional.empty()))
            .isEmpty();
    }

    @Test
    public void toZoneDateTimeShouldWithNullValue() throws Exception {
        assertThat(ZonedDateTimeConverter.toZonedDateTime(
            Optional.empty(),
            Optional.of(DEFAULT_ZONE)))
            .isEmpty();
    }

    @Test(expected = IllegalArgumentException.class)
    public void toZoneDateTimeShouldThrowOnNullZoneId() throws Exception {
        ZonedDateTimeConverter.toZonedDateTime(Optional.of(FORMATTER.parse(DATE_STRING)), Optional.empty());
    }

    @Test
    public void toZoneDateTimeShouldWorkWithAnotherTimeZone() throws Exception {
        assertThat(ZonedDateTimeConverter.toZonedDateTime(
            Optional.of(FORMATTER.parse(DATE_STRING)),
            Optional.of(OTHER_ZONE)))
            .isEqualTo(Optional.of(ZONED_DATE_TIME_OTHER_ZONE));
    }

    @Test
    public void toZoneDateTimeShouldReturnNullIfBothDateAndZoneAreNull() throws Exception {
        assertThat(ZonedDateTimeConverter.toZonedDateTime(Optional.empty(), Optional.empty()))
            .isEmpty();
    }

}
