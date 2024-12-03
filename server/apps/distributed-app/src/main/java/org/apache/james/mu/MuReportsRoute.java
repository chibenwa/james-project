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

package org.apache.james.mu;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.inject.Inject;

import org.apache.james.util.DurationParser;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.JsonTransformer;

import com.google.common.base.Preconditions;

import spark.Service;

public class MuReportsRoute implements Routes {
    public record MailReportEntryDAO(org.apache.james.mu.MailReportEntry.Kind kind,
                                  String subject,
                                  String sender,
                                  String recipient,
                                  Instant date) {
        public static MailReportEntryDAO from(MailReportEntry entry) {
            return new MailReportEntryDAO(entry.kind(), entry.subject(), entry.sender().asString("<>"), entry.recipient().asString(), entry.date());
        }
    }

    private final MailReportGenerator receivedMailReportGenerator;
    private final Clock clock;

    @Inject
    public MuReportsRoute(MailReportGenerator receivedMailReportGenerator, Clock clock) {
        this.receivedMailReportGenerator = receivedMailReportGenerator;
        this.clock = clock;
    }

    @Override
    public String getBasePath() {
        return "/mu/reports";
    }

    @Override
    public void define(Service service) {
        service.get(getBasePath() + "/mails", (request, response) -> {
            String rawDuration = request.queryParams("duration");
            Preconditions.checkArgument(rawDuration != null, "'duration' is a mandatory parameter");
            Duration duration = DurationParser.parse(rawDuration);
            Instant now = clock.instant();
            Instant reportStart = now.minus(duration);
            return receivedMailReportGenerator.generateReport(reportStart, now)
                .map(MailReportEntryDAO::from)
                .collectList()
                .block();
        }, new JsonTransformer());
    }
}
