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

package org.apache.mailet.base;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.junit.Before;
import org.junit.Test;

public class MailetUtilTest {

    private static final String A_PARAMETER = "aParameter";

    FakeMailetConfig config;

    @Before
    public void setUp() throws Exception {
        config = new FakeMailetConfig();
    }

    @Test
    public void testGetInitParameterParameterIsTrue() {
        assertTrue(getParameterValued("true", true));
        assertTrue(getParameterValued("true", false));
        assertTrue(getParameterValued("TRUE", true));
        assertTrue(getParameterValued("TRUE", false));
        assertTrue(getParameterValued("trUE", true));
        assertTrue(getParameterValued("trUE", false));
    }

    @Test
    public void testGetInitParameterParameterIsFalse() {
        assertFalse(getParameterValued("false", true));
        assertFalse(getParameterValued("false", false));
        assertFalse(getParameterValued("FALSE", true));
        assertFalse(getParameterValued("FALSE", false));
        assertFalse(getParameterValued("fALSe", true));
        assertFalse(getParameterValued("fALSe", false));
    }

    @Test
    public void testGetInitParameterParameterDefaultsToTrue() {
        assertTrue(getParameterValued("fals", true));
        assertTrue(getParameterValued("TRU", true));
        assertTrue(getParameterValued("FALSEest", true));
        assertTrue(getParameterValued("", true));
        assertTrue(getParameterValued("gubbins", true));
    }

    @Test
    public void testGetInitParameterParameterDefaultsToFalse() {
        assertFalse(getParameterValued("fals", false));
        assertFalse(getParameterValued("TRU", false));
        assertFalse(getParameterValued("FALSEest", false));
        assertFalse(getParameterValued("", false));
        assertFalse(getParameterValued("gubbins", false));
    }

    private boolean getParameterValued(String value, boolean defaultValue) {
        config.clear();
        config.setProperty(A_PARAMETER, value);
        return MailetUtil.getInitParameter(config, A_PARAMETER, defaultValue);
    }

    @Test
    public void ownerIsAMailingListPrefix() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("owner-list@any.com"));

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void requestIsAMailingListPrefix() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("list-request@any.com"));

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void mailerDaemonIsReserved() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("MAILER-DAEMON@any.com"));

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listservIsReserved() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("LISTSERV@any.com"));

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void majordomoIsReserved() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("majordomo@any.com"));

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listIdShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Id", "any");
        fakeMail.setMessage(message);

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listHelpShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Help", "any");
        fakeMail.setMessage(message);

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listSubscribeShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Subscribe", "any");
        fakeMail.setMessage(message);

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listUnsubscribeShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Unsubscribe", "any");
        fakeMail.setMessage(message);

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listPostShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Post", "any");
        fakeMail.setMessage(message);

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listOwnerShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Owner", "any");
        fakeMail.setMessage(message);

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listArchiveShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Archive", "any");
        fakeMail.setMessage(message);

        assertThat(MailetUtil.isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void normalMailShouldNotBeIdentifiedAsMailingList() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        fakeMail.setMessage(new MimeMessage(Session.getDefaultInstance(new Properties())));

        assertThat(MailetUtil.isMailingList(fakeMail)).isFalse();
    }

    @Test
    public void isAutoSubmittedShouldNotMatchNonAutoSubmittedMails() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setMessage(new MimeMessage(Session.getDefaultInstance(new Properties())));

        assertThat(MailetUtil.isAutoSubmitted(fakeMail)).isFalse();
    }

    @Test
    public void isAutoSubmittedShouldWork() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("Auto-Submitted", "auto-replied");
        fakeMail.setMessage(message);

        assertThat(MailetUtil.isAutoSubmitted(fakeMail)).isTrue();
    }

    @Test
    public void isMdnSentAutomaticallyShouldWork() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    "Disposition: MDN-sent-automatically",
                    "message/disposition-notification;")
            ));
        scriptPart.setHeader("Content-Type", "message/disposition-notification");
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);

        fakeMail.setMessage(message);

        assertThat(MailetUtil.isMdnSentAutomatically(fakeMail)).isTrue();
    }

    @Test
    public void isMdnSentAutomaticallyShouldNotFilterManuallySentMdn() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    "Disposition: MDN-sent-manually",
                    "message/disposition-notification; charset=UTF-8")
            ));
        scriptPart.setHeader("Content-Type", "message/disposition-notification");
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);

        fakeMail.setMessage(message);

        assertThat(MailetUtil.isMdnSentAutomatically(fakeMail)).isFalse();
    }

    @Test
    public void isMdnSentAutomaticallyShouldManageItsMimeType() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    "Disposition: MDN-sent-automatically",
                    "text/plain")
            ));
        scriptPart.setHeader("Content-Type", "text/plain");
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);

        fakeMail.setMessage(message);

        assertThat(MailetUtil.isMdnSentAutomatically(fakeMail)).isFalse();
    }

}
