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

package org.apache.james.backends.cassandra.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.Test;

public class ZonedDateTimeRepresentationTest {

    private static final ZonedDateTime ZONED_DATE_TIME = ZonedDateTime.parse("2016-04-13T12:04:40.906+07:00[Asia/Vientiane]");

    @Test
    public void convertToZonedDateTimeShouldWork() {
        assertThat(ZonedDateTimeRepresentation.fromZonedDateTime(ZONED_DATE_TIME).convertToZonedDateTime()).isEqualTo(ZONED_DATE_TIME);
    }

}
