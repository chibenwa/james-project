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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

import com.google.common.base.Preconditions;

public class ZonedDateTimeConverter {

    public static Optional<Date> toDate(Optional<ZonedDateTime> zonedDateTime) {
        return zonedDateTime.map(time -> new Date(time.toInstant().toEpochMilli()));
    }

    public static Optional<ZonedDateTime> toZonedDateTime(Optional<Date> date, Optional<ZoneId> zoneId) {
        Preconditions.checkArgument(zoneId.isPresent() || !date.isPresent(), "ZoneId should be specified when date is specified");
        return date.map(d -> ZonedDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()).withZoneSameInstant(zoneId.get()));
    }

}
