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
import java.time.Instant;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.util.ReactorUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import reactor.core.publisher.Flux;

public class MuReceivedReportRecorder extends GenericMailet {
    private final MailReportGenerator mailReportGenerator;
    private final Clock clock;
    private MailReportEntry.Kind kind;

    @Inject
    public MuReceivedReportRecorder(MailReportGenerator mailReportGenerator, Clock clock) {
        this.mailReportGenerator = mailReportGenerator;
        this.clock = clock;
    }

    @Override
    public void init() throws MessagingException {
        kind = Optional.ofNullable(getInitParameter("kind"))
            .flatMap(MailReportEntry.Kind::parse)
            .orElseThrow(() -> new MessagingException("Unparsable or missing kind property"));
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        Instant instant = clock.instant();
        String subject = Optional.ofNullable(mail.getMessage().getSubject()).orElse("<no subject>");

        Flux.fromIterable(mail.getRecipients())
            .map(recipient -> new MailReportEntry(kind,
                subject,
                mail.getMaybeSender(),
                recipient,
                instant))
            .flatMap(mailReportGenerator::append, ReactorUtils.DEFAULT_CONCURRENCY)
            .then()
            .block();
    }
}
