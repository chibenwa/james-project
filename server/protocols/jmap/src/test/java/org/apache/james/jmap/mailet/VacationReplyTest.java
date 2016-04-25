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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.mailbox.store.extractor.ParsedContent;
import org.apache.james.mailbox.store.extractor.TextExtractor;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class VacationReplyTest {

    public static final String REASON = "I am in vacation dudes !";
    public static final String SUBJECT = "subject";
    public static final String HTML_REASON = "<p>I am in vacation dudes !</p>";

    private TextExtractor textExtractor;
    private MailAddress originalSender;
    private MailAddress originalRecipient;
    private FakeMail mail;

    @Before
    public void setUp() throws Exception {
        textExtractor = mock(TextExtractor.class);

        originalSender = new MailAddress("distant@apache.org");
        originalRecipient = new MailAddress("benwa@apache.org");
        mail = new FakeMail();
        mail.setMessage(new MimeMessage(Session.getInstance(new Properties()) ,ClassLoader.getSystemResourceAsStream("spamMail.eml")));
        mail.setSender(originalSender);
    }

    @Test
    public void vacationReplyShouldWork() throws Exception {
        VacationReply vacationReply = VacationReply.builder(mail)
            .vacation(Vacation.builder()
                .enabled(true)
                .textBody(Optional.of(REASON))
                .htmlBody(Optional.of(HTML_REASON))
                .subject(Optional.of(SUBJECT))
                .build())
            .mailRecipient(originalRecipient)
            .build(textExtractor);

        assertThat(vacationReply.getRecipients()).containsExactly(originalSender);
        assertThat(vacationReply.getSender()).isEqualTo(originalRecipient);
        assertThat(vacationReply.getMimeMessage().getHeader("subject")).containsExactly(SUBJECT);
        assertThat(IOUtils.toString(vacationReply.getMimeMessage().getInputStream())).contains(REASON);
        assertThat(IOUtils.toString(vacationReply.getMimeMessage().getInputStream())).contains(HTML_REASON);
    }

    @Test
    public void vacationReplyShouldExtractPlainTextContentWhenOnlyHtmlBody() throws Exception {
        when(textExtractor.extractContent(any(), eq("text/html"), eq(""))).thenReturn(new ParsedContent(REASON, ImmutableMap.of()));

        VacationReply vacationReply = VacationReply.builder(mail)
            .vacation(Vacation.builder()
                .enabled(true)
                .htmlBody(Optional.of(HTML_REASON))
                .build())
            .mailRecipient(originalRecipient)
            .build(textExtractor);

        assertThat(vacationReply.getRecipients()).containsExactly(originalSender);
        assertThat(vacationReply.getSender()).isEqualTo(originalRecipient);
        assertThat(IOUtils.toString(vacationReply.getMimeMessage().getInputStream())).contains(REASON);
        assertThat(IOUtils.toString(vacationReply.getMimeMessage().getInputStream())).contains(HTML_REASON);
    }

    @Test
    public void vacationReplyShouldAddReSuffixToSubjectByDefault() throws Exception {
        VacationReply vacationReply = VacationReply.builder(mail)
            .vacation(Vacation.builder()
                .enabled(true)
                .textBody(Optional.of(REASON))
                .build())
            .mailRecipient(originalRecipient)
            .build(textExtractor);

        assertThat(vacationReply.getMimeMessage().getHeader("subject")).containsExactly("Re: Original subject");
    }

    @Test(expected = MessagingException.class)
    public void vacationReplyShouldPropagateHTMLParsingException() throws Exception {
        when(textExtractor.extractContent(any(), eq("text/html"), eq(""))).thenThrow(new Exception());

        VacationReply.builder(mail)
            .vacation(Vacation.builder()
                .enabled(true)
                .htmlBody(Optional.of(HTML_REASON))
                .build())
            .mailRecipient(originalRecipient)
            .build(textExtractor);
    }

    @Test(expected = IllegalStateException.class)
    public void vacationReplyShouldThrowWhenNoBody() throws Exception {
        VacationReply.builder(mail)
            .vacation(Vacation.builder()
                .enabled(true)
                .build())
            .mailRecipient(originalRecipient)
            .build(textExtractor);
    }

    @Test(expected = NullPointerException.class)
    public void vacationReplyShouldThrowOnNullMail() {
        VacationReply.builder(null);
    }

    @Test(expected = NullPointerException.class)
    public void vacationReplyShouldThrowOnNullOriginalEMailAddress() throws Exception {
        VacationReply.builder(new FakeMail())
            .mailRecipient(null)
            .build(textExtractor);
    }

}
