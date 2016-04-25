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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.mailbox.store.extractor.TextExtractor;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

public class VacationReply {

    public static class Builder {

        private final Mail originalMail;
        private MailAddress mailRecipient;
        private Vacation vacation;

        public Builder(Mail originalMail) {
            Preconditions.checkNotNull(originalMail, "Origin mail shall not be null");
            this.originalMail = originalMail;
        }

        public Builder mailRecipient(MailAddress mailRecipient) {
            this.mailRecipient = mailRecipient;
            return this;
        }

        public Builder vacation(Vacation vacation) {
            this.vacation = vacation;
            return this;
        }

        public VacationReply build(TextExtractor textExtractor) throws MessagingException {
            Preconditions.checkNotNull(mailRecipient, "Original recipient address should not be null");
            Preconditions.checkState(vacation.getHtmlBody().isPresent() || vacation.getTextBody().isPresent());

            MimeMessage reply = (MimeMessage) originalMail.getMessage().reply(false);
            reply.setContent(generateNotificationContent(textExtractor));
            Optional<String> subject = vacation.getSubject();
            if (subject.isPresent()) {
                reply.setHeader("subject", subject.get());
            }

            return new VacationReply(mailRecipient, Lists.newArrayList(originalMail.getSender()), reply);
        }

        private Multipart generateNotificationContent(TextExtractor textExtractor) throws MessagingException {
            try {
                Multipart multipart = new MimeMultipart("mixed");
                addPlainPart(multipart, vacation.getTextBody().orElseGet(() -> extractTextFromHTMLPart(textExtractor)));
                addHtmlPart(multipart, vacation.getHtmlBody());
                return multipart;
            } catch (IOException e) {
                throw new MessagingException("Cannot read specified content", e);
            } catch (RuntimeException runtimeException) {
                if (runtimeException.getCause() instanceof MessagingException) {
                    throw (MessagingException) runtimeException.getCause();
                } else {
                    throw Throwables.propagate(runtimeException);
                }
            }
        }

        private String extractTextFromHTMLPart(TextExtractor textExtractor) throws RuntimeException {
            InputStream htmlInputStream = new ByteArrayInputStream(vacation.getHtmlBody().get().getBytes());
            try {
                return textExtractor.extractContent(htmlInputStream, "text/html", "").getTextualContent();
            } catch (Exception e) {
                throw new RuntimeException(new MessagingException("Can not parse HTML body", e));
            }
        }

        private Multipart addPlainPart(Multipart multipart, String text) throws MessagingException, IOException {
                return addTextPart(multipart, text, "text/plain");
        }

        private Multipart addHtmlPart(Multipart multipart, Optional<String> htmlOptional) throws MessagingException, IOException {
            if (htmlOptional.isPresent()) {
                return addTextPart(multipart, htmlOptional.get(), "text/html");
            }
            return multipart;
        }

        private Multipart addTextPart(Multipart multipart, String text, String contentType) throws MessagingException, IOException {
            MimeBodyPart textReasonPart = new MimeBodyPart();
            textReasonPart.setDataHandler(
                new DataHandler(
                    new ByteArrayDataSource(
                        text,
                        contentType + "; charset=UTF-8")));
            textReasonPart.setDisposition(MimeBodyPart.INLINE);
            multipart.addBodyPart(textReasonPart);
            return multipart;
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

