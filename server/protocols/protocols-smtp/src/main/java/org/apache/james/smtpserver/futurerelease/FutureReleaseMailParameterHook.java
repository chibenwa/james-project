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

package org.apache.james.smtpserver.futurerelease;

import static org.apache.james.protocols.api.ProtocolSession.State.Transaction;
import static org.apache.james.smtpserver.futurerelease.FutureReleaseParameters.HOLDFOR_PARAMETER;
import static org.apache.james.smtpserver.futurerelease.FutureReleaseParameters.HOLDUNTIL_PARAMETER;
import static org.apache.james.smtpserver.futurerelease.FutureReleaseParameters.MAX_HOLD_FOR_SUPPORTED;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.MailParametersHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FutureReleaseMailParameterHook implements MailParametersHook {

    private static final int UTC_TIMESTAMP_LENGTH = 20;
    private static final int ZONED_TIMESTAMP_LENGTH = 25;
    private static final Logger LOGGER = LoggerFactory.getLogger(FutureReleaseMailParameterHook.class);
    public static final ProtocolSession.AttachmentKey<FutureReleaseParameters.HoldFor> FUTURERELEASE_HOLDFOR = ProtocolSession.AttachmentKey.of("FUTURERELEASE_HOLDFOR", FutureReleaseParameters.HoldFor.class);
    private static final Instant now = ZonedDateTime.parse("2023-04-13T11:00:00Z").toInstant();
    public static final long INVALID_HOLDUNTIL_VALUE = -1L;
    private static final long NUMBER_PARSE_EXCEPTION = -2L;
    private static final long DATE_TIME_FORMAT_EXCEPTION = -3L;
    private static final long INVALID_PARAM_NAME = -4L;

    @Override
    public HookResult doMailParameter(SMTPSession session, String paramName, String paramValue) {
        long requestedHoldFor = evaluateHoldFor(paramName, paramValue);
        if (requestedHoldFor > MAX_HOLD_FOR_SUPPORTED) {
            LOGGER.debug("HoldFor is greater than max-future-release-interval or holdUntil exceeded max-future-release-date-time");
            return HookResult.DENY;
        }
        if (requestedHoldFor < 0) {
            LOGGER.debug("HoldFor value is negative or holdUntil value is before now");
            return HookResult.DENY;
        }
        if (session.getAttachment(FUTURERELEASE_HOLDFOR, Transaction).isPresent()) {
            LOGGER.debug("Mail parameter cannot contains both holdFor and holdUntil parameters");
            return HookResult.DENY;
        }
        session.setAttachment(FUTURERELEASE_HOLDFOR, FutureReleaseParameters.HoldFor.of(requestedHoldFor), Transaction);
        return HookResult.DECLINED;
    }

    private static Long evaluateHoldFor(String paramName, String paramValue) {
        try {
            if (paramName.equals(HOLDFOR_PARAMETER)) {
                return Long.parseLong(paramValue);
            }
            if (selectDateTimeFormatter(paramValue).equals(DateTimeFormatter.ISO_DATE)) {
                return INVALID_HOLDUNTIL_VALUE;
            }
            if (paramName.equals(HOLDUNTIL_PARAMETER)) {
                DateTimeFormatter formatter = selectDateTimeFormatter(paramValue);
                return Duration.between(now, ZonedDateTime.parse(paramValue, formatter).toInstant()).toSeconds();
            }
        } catch (NumberFormatException e) {
            LOGGER.debug("Failed to parse number format");
            return NUMBER_PARSE_EXCEPTION;
        } catch (DateTimeParseException e) {
            LOGGER.debug("Failed to parse date format");
            return DATE_TIME_FORMAT_EXCEPTION;
        }
        return INVALID_PARAM_NAME;
    }

    private static DateTimeFormatter selectDateTimeFormatter(String dateTime) {
        if (dateTime.length() == UTC_TIMESTAMP_LENGTH) {
            return DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"));
        }
        if (dateTime.length() == ZONED_TIMESTAMP_LENGTH) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        }
        return DateTimeFormatter.ISO_DATE;
    }
    @Override
    public String[] getMailParamNames() {
        return new String[] {HOLDFOR_PARAMETER, HOLDUNTIL_PARAMETER};
    }
}
