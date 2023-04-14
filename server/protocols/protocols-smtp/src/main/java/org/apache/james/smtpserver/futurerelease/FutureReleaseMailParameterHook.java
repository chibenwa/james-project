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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import javax.inject.Inject;

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

    private final Clock clock;

    @Inject
    public FutureReleaseMailParameterHook(Clock clock) {
        this.clock = clock;
    }

    @Override
    public HookResult doMailParameter(SMTPSession session, String paramName, String paramValue) {
        try {
            Duration requestedHoldFor = evaluateHoldFor(paramName, paramValue);

            if (requestedHoldFor.compareTo(MAX_HOLD_FOR_SUPPORTED) > 0) {
                LOGGER.debug("HoldFor is greater than max-future-release-interval or holdUntil exceeded max-future-release-date-time");
                return HookResult.DENY;
            }
            if (requestedHoldFor.isNegative()) {
                LOGGER.debug("HoldFor value is negative or holdUntil value is before now");
                return HookResult.DENY;
            }
            if (session.getAttachment(FUTURERELEASE_HOLDFOR, Transaction).isPresent()) {
                LOGGER.debug("Mail parameter cannot contains both holdFor and holdUntil parameters");
                return HookResult.DENY;
            }
            session.setAttachment(FUTURERELEASE_HOLDFOR, FutureReleaseParameters.HoldFor.of(requestedHoldFor), Transaction);
            return HookResult.DECLINED;
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Incorrect syntax when handling FUTURE-RELEASE mail parameter", e);
            return HookResult.DENY;
        }
    }

    private Duration evaluateHoldFor(String paramName, String paramValue) {
        if (paramName.equals(HOLDFOR_PARAMETER)) {
            return Duration.ofSeconds(Long.parseLong(paramValue));
        }
        if (paramName.equals(HOLDUNTIL_PARAMETER)) {
            DateTimeFormatter formatter = selectDateTimeFormatter(paramValue);
            Instant now = LocalDateTime.now(clock).toInstant(ZoneOffset.UTC);
            return Duration.between(now, ZonedDateTime.parse(paramValue, formatter).toInstant());
        }
        throw new IllegalArgumentException("Invalid parameter name " + paramName);
    }

    private DateTimeFormatter selectDateTimeFormatter(String dateTime) {
        if (dateTime.length() == UTC_TIMESTAMP_LENGTH) {
            return DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"));
        }
        if (dateTime.length() == ZONED_TIMESTAMP_LENGTH) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        }
        throw new IllegalArgumentException("Date time pattern is unrecognized");
    }

    @Override
    public String[] getMailParamNames() {
        return new String[] {HOLDFOR_PARAMETER, HOLDUNTIL_PARAMETER};
    }
}
