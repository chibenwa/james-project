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

import java.io.IOException;
import java.util.List;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class VacationReply {

    public static class Builder {

        private final Mail originalMail;
        private MailAddress mailRecipient;
        private String reason;

        public Builder(Mail originalMail) {
            Preconditions.checkNotNull(originalMail, "Origin mail shall not be null");
            this.originalMail = originalMail;
        }

        public Builder mailRecipient(MailAddress mailRecipient) {
            this.mailRecipient = mailRecipient;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public VacationReply build() throws MessagingException {
            Preconditions.checkNotNull(mailRecipient, "Original recipient address should not be null");

            MimeMessage reply = (MimeMessage) originalMail.getMessage().reply(false);
            reply.setSubject(generateNotificationSubject());
            reply.setContent(generateNotificationContent());

            return new VacationReply(mailRecipient, Lists.newArrayList(originalMail.getSender()), reply);
        }

        private String generateNotificationSubject() {
            return mailRecipient + " is currently in vacation";
        }

        private Multipart generateNotificationContent() throws MessagingException {
            try {
                Multipart multipart = new MimeMultipart("mixed");
                MimeBodyPart reasonPart = new MimeBodyPart();
                reasonPart.setDataHandler(
                    new DataHandler(
                        new ByteArrayDataSource(
                            reason,
                            "text/plain; charset=UTF-8")));
                reasonPart.setDisposition(MimeBodyPart.INLINE);
                multipart.addBodyPart(reasonPart);
                return multipart;
            } catch (IOException e) {
                throw new MessagingException("Cannot read specified content", e);
            }
        }
    }

    public static Builder builder(Mail originalMail) {
        return new Builder(originalMail);
    }

    private final MailAddress sender;
    private final List<MailAddress> recipients;
    private final MimeMessage mimeMessage;

    private VacationReply(MailAddress sender, List<MailAddress> recipients, MimeMessage mimeMessage) {
        this.sender = sender;
        this.recipients = recipients;
        this.mimeMessage = mimeMessage;
    }

    public MailAddress getSender() {
        return sender;
    }

    public List<MailAddress> getRecipients() {
        return recipients;
    }

    public MimeMessage getMimeMessage() {
        return mimeMessage;
    }
}

