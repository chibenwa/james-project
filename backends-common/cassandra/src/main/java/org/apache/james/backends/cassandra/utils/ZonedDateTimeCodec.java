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

import static com.datastax.driver.core.ParseUtils.isLongLiteral;
import static com.datastax.driver.core.ParseUtils.quote;
import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;

import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.ParseUtils;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.UserType;
import com.datastax.driver.core.exceptions.InvalidTypeException;

public class ZonedDateTimeCodec extends TypeCodec.AbstractUDTCodec<ZonedDateTime> {

    private static final java.time.format.DateTimeFormatter FORMATTER = new java.time.format.DateTimeFormatterBuilder()
    .parseCaseSensitive()
    .parseStrict()
    .append(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
    .optionalStart()
    .appendLiteral('T')
    .appendValue(java.time.temporal.ChronoField.HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(java.time.temporal.ChronoField.MINUTE_OF_HOUR, 2)
    .optionalEnd()
    .optionalStart()
    .appendLiteral(':')
    .appendValue(java.time.temporal.ChronoField.SECOND_OF_MINUTE, 2)
    .optionalEnd()
    .optionalStart()
    .appendFraction(java.time.temporal.ChronoField.NANO_OF_SECOND, 0, 9, true)
    .optionalEnd()
    .optionalStart()
    .appendZoneOrOffsetId()
    .optionalEnd()
    .toFormatter()
    .withZone(java.time.ZoneOffset.UTC);

    public ZonedDateTimeCodec(UserType definition) {
        super(definition, java.time.ZonedDateTime.class);
        checkArgument(definition.getFieldType(CassandraZonedDateTimeModule.DATE).equals(DataType.timestamp()),
            "Expected UDT with field %s being a timestamp. Got %s",
            CassandraZonedDateTimeModule.DATE,
            definition.getFieldType(CassandraZonedDateTimeModule.DATE));
        checkArgument(definition.getFieldType(CassandraZonedDateTimeModule.TIME_ZONE).equals(DataType.varchar()),
            "Expected UDT with field %s being a text. Got %s",
            CassandraZonedDateTimeModule.TIME_ZONE,
            definition.getFieldType(CassandraZonedDateTimeModule.TIME_ZONE));
    }

    @Override
    protected ZonedDateTime newInstance() {
        return null;
    }

    @Override
    protected ByteBuffer serializeField(ZonedDateTime zonedDateTime, String fieldName, ProtocolVersion protocolVersion) {
        switch (fieldName) {
            case CassandraZonedDateTimeModule.DATE:
                return timestamp().serialize(new Date(zonedDateTime.toInstant().toEpochMilli()), protocolVersion);
            case CassandraZonedDateTimeModule.TIME_ZONE:
                return varchar().serialize(zonedDateTime.getZone().getId(), protocolVersion);
            default:
                throw new InvalidTypeException(fieldName + " is not known as a UDT field used for ZonedDateTime");
        }
    }

    @Override
    protected ZonedDateTime deserializeAndSetField(ByteBuffer byteBuffer, ZonedDateTime zonedDateTime, String fieldName, ProtocolVersion protocolVersion) {
        switch (fieldName) {
            case CassandraZonedDateTimeModule.DATE:
                return ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp()
                        .deserialize(byteBuffer, protocolVersion)
                        .getTime()),
                    ZoneOffset.UTC);
            case CassandraZonedDateTimeModule.TIME_ZONE:
                return zonedDateTime.withZoneSameInstant(
                    ZoneId.of(varchar().deserialize(byteBuffer, protocolVersion)));
            default:
                throw new InvalidTypeException(fieldName + " is not known as a UDT field used for ZonedDateTime");
        }
    }

    @Override
    protected String formatField(ZonedDateTime zonedDateTime, String fieldName) {
        switch (fieldName) {
            case CassandraZonedDateTimeModule.DATE:
                return quote(FORMATTER.format(zonedDateTime));
            case CassandraZonedDateTimeModule.TIME_ZONE:
                return quote(zonedDateTime.getZone().getId());
            default:
                throw new InvalidTypeException(fieldName + " is not known as a UDT field used for ZonedDateTime");
        }
    }

    @Override
    protected ZonedDateTime parseAndSetField(String input, ZonedDateTime zonedDateTime, String fieldName) {
        System.out.println("parseAndSetField");
        switch (fieldName) {
            case CassandraZonedDateTimeModule.DATE:
                return parseDate(input);
            case CassandraZonedDateTimeModule.TIME_ZONE:
                return zonedDateTime.withZoneSameInstant(ZoneId.of(input));
            default:
                throw new InvalidTypeException(fieldName + " is not known as a UDT field used for ZonedDateTime");
        }
    }

    private ZonedDateTime parseDate(String input) {
        String unquotedInput = unquoteIfNeeded(input);
        if (isLongLiteral(input)) {
            return parseFromLong(input, unquotedInput);
        } else {
            return parseFromFormattedDate(input);
        }
    }

    private ZonedDateTime parseFromFormattedDate(String input) {
        try {
            return ZonedDateTime.from(FORMATTER.parse(input));
        } catch (DateTimeParseException e) {
            throw new InvalidTypeException(String.format("Cannot parse timestamp value from \"%s\"", input));
        }
    }

    private ZonedDateTime parseFromLong(String input, String unquotedInput) {
        try {
            return ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(Long.parseLong(unquotedInput)), ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            throw new InvalidTypeException(String.format("Cannot parse timestamp value from \"%s\"", input));
        }
    }

    private String unquoteIfNeeded(String input) {
        if (ParseUtils.isQuoted(input)) {
            return ParseUtils.unquote(input);
        }
        return input;
    }
}
