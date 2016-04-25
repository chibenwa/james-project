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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.NotificationRegistry;
import org.apache.james.jmap.api.vacation.RecipientId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.mailbox.store.extractor.TextExtractor;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class VacationMailetTest {

    public static final ZonedDateTime DATE_TIME_1 = ZonedDateTime.parse("2016-10-09T08:07:06+07:00[Asia/Vientiane]");
    public static final ZonedDateTime DATE_TIME_2 = ZonedDateTime.parse("2017-10-09T08:07:06+07:00[Asia/Vientiane]");
    public static final ZonedDateTime DATE_TIME_3 = ZonedDateTime.parse("2018-10-09T08:07:06+07:00[Asia/Vientiane]");

    public static final String USERNAME = "benwa@apache.org";
    public static final AccountId ACCOUNT_ID = AccountId.fromString(USERNAME);
    private VacationMailet testee;
    private VacationRepository vacationRepository;
    private ZonedDateTimeProvider zonedDateTimeProvider;
    private NotificationRegistry notificationRegistry;
    private TextExtractor textExtractor;
    private FakeMailContext fakeMailContext;
    private MailAddress originalSender;
    private MailAddress originalRecipient;
    private RecipientId recipientId;

    @Before
    public void setUp() throws Exception {
        this.originalSender = new MailAddress("distant@apache.org");
        this.originalRecipient = new MailAddress(USERNAME);
        recipientId = RecipientId.fromMailAddress(originalSender);

        vacationRepository = mock(VacationRepository.class);
        zonedDateTimeProvider = mock(ZonedDateTimeProvider.class);
        notificationRegistry = mock(NotificationRegistry.class);
        textExtractor = mock(TextExtractor.class);
        testee = new VacationMailet(vacationRepository, zonedDateTimeProvider, notificationRegistry, textExtractor);
        fakeMailContext = new FakeMailContext();
        testee.init(new FakeMailetConfig("vacation", fakeMailContext));
    }

    @Test
    public void unactivatedVacationShouldNotSendNotification() throws Exception {
        FakeMail mail = createFakeMail();
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2);
        when(vacationRepository.retrieveVacation(ACCOUNT_ID))
            .thenReturn(CompletableFuture.completedFuture(VacationRepository.DEFAULT_VACATION));

        testee.service(mail);

        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void activateVacationShouldSendNotification() throws Exception {
        FakeMail mail = createFakeMail();
        when(vacationRepository.retrieveVacation(ACCOUNT_ID))
            .thenReturn(CompletableFuture.completedFuture(
                Vacation.builder()
                    .enabled(true)
                    .fromDate(Optional.of(DATE_TIME_1))
                    .toDate(Optional.of(DATE_TIME_3))
                    .textBody(Optional.of("Explaining my vacation"))
                    .build()));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2);
        when(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId))
            .thenReturn(CompletableFuture.completedFuture(false));

        testee.service(mail);

        FakeMailContext.SentMail expected = new FakeMailContext.SentMail(originalRecipient, ImmutableList.of(originalSender), null);
        assertThat(fakeMailContext.getSentMails()).containsExactly(expected);

        verify(notificationRegistry).isRegistered(ACCOUNT_ID, recipientId);
        verify(notificationRegistry).register(ACCOUNT_ID, recipientId, Optional.of(DATE_TIME_3));
        verifyNoMoreInteractions(notificationRegistry);
    }

    @Test
    public void activateVacationShouldNotSendNotificationIfAlreadySent() throws Exception {
        FakeMail mail = createFakeMail();
        when(vacationRepository.retrieveVacation(ACCOUNT_ID))
            .thenReturn(CompletableFuture.completedFuture(
                Vacation.builder()
                    .enabled(true)
                    .fromDate(Optional.of(DATE_TIME_1))
                    .toDate(Optional.of(DATE_TIME_3))
                    .textBody(Optional.of("Explaining my vacation"))
                    .build()));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2);
        when(notificationRegistry.isRegistered(ACCOUNT_ID, RecipientId.fromMailAddress(originalSender)))
            .thenReturn(CompletableFuture.completedFuture(true));

        testee.service(mail);

        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void activateVacationShouldNotSendNotificationToMailingList() throws Exception {
        FakeMail mail = createFakeMail();
        mail.setSender(new MailAddress("owner-list@any.com"));
        when(vacationRepository.retrieveVacation(ACCOUNT_ID))
            .thenReturn(CompletableFuture.completedFuture(
                Vacation.builder()
                    .enabled(true)
                    .fromDate(Optional.of(DATE_TIME_1))
                    .toDate(Optional.of(DATE_TIME_3))
                    .textBody(Optional.of("Explaining my vacation"))
                    .build()));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2);
        when(notificationRegistry.isRegistered(ACCOUNT_ID, RecipientId.fromMailAddress(originalSender)))
            .thenReturn(CompletableFuture.completedFuture(false));

        testee.service(mail);

        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    private FakeMail createFakeMail() throws MessagingException {
        FakeMail mail = new FakeMail();
        mail.setMessage(new MimeMessage(Session.getInstance(new Properties()) ,ClassLoader.getSystemResourceAsStream("spamMail.eml")));
        mail.setSender(originalSender);
        mail.setRecipients(ImmutableList.of(originalRecipient));
        return mail;
    }

}
