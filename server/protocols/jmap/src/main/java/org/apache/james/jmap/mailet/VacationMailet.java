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

package org.apache.james.jmap.mailet;

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.utils.ZonedDateTimeProvider;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VacationMailet extends GenericMailet {

    private static final Logger LOGGER = LoggerFactory.getLogger(VacationMailet.class);

    public class ContextualizedValue<T> {
        private final MailAddress recipient;
        private final T value;

        public ContextualizedValue(MailAddress recipient, T value) {
            this.recipient = recipient;
            this.value = value;
        }

        public MailAddress getRecipient() {
            return recipient;
        }

        public T getValue() {
            return value;
        }
    }

    private final VacationRepository vacationRepository;
    private final ZonedDateTimeProvider zonedDateTimeProvider;

    @Inject
    public VacationMailet(VacationRepository vacationRepository, ZonedDateTimeProvider zonedDateTimeProvider) {
        this.vacationRepository = vacationRepository;
        this.zonedDateTimeProvider = zonedDateTimeProvider;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        mail.getRecipients()
            .stream()
            .map(mailAddress -> new ContextualizedValue<>(mailAddress,
                AccountId.create(mailAddress.getLocalPart() + "@" + mailAddress.getDomain())))
            .map(accountIdWithContext -> new ContextualizedValue<>(accountIdWithContext.getRecipient(),
                vacationRepository.retrieveVacation(accountIdWithContext.getValue())))
            .map(vacationWithContext -> manageVacation(vacationWithContext, mail))
            .forEach(CompletableFuture::join);
    }

    public CompletableFuture<Vacation> manageVacation(ContextualizedValue<CompletableFuture<Vacation>> contextualizedVacation, Mail processedMail) {
       return contextualizedVacation.getValue().whenCompleteAsync((vacation, exception) -> {
           if (vacation.isActiveAtDate(zonedDateTimeProvider.get())) {
               sendNotification(contextualizedVacation.getRecipient(), processedMail, vacation);
           }
       });
    }

    private void sendNotification(MailAddress recipient, Mail processedMail, Vacation vacation) {
        try {
            VacationReply vacationReply = VacationReply.builder(processedMail)
                .mailRecipient(recipient)
                .reason(vacation.getTextBody())
                .build();
            getMailetContext().sendMail(vacationReply.getSender(),
                vacationReply.getRecipients(),
                vacationReply.getMimeMessage());
        } catch (MessagingException e) {
            LOGGER.warn("Failed to send JMAP vacation notification from {} to {}", recipient, processedMail.getSender(), e);
        }
    }
}
