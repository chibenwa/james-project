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

package org.apache.james.mailbox.store.search;

import static org.apache.james.mailbox.model.SearchQuery.bodyContains;
import static org.apache.james.mailbox.model.SearchQuery.mailContains;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Locale;

import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.MessageBuilder;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchUtilsRFC822Test {
    static final String FROM_ADDRESS = "Harry <harry@example.org";

    static final String SUBJECT_PART = "Mixed";

    static final String CUSTARD = "CUSTARD";

    static final String RHUBARD = "Rhubard";

    static final String BODY = "This is a simple email\r\n "
            + "It has " + RHUBARD + ".\r\n" + "It has " + CUSTARD + ".\r\n"
            + "It needs naught else.\r\n";

    MailboxMessage row;

    MessageSearches messageSearches;

    @BeforeEach
    void setUp() throws Exception {
        MessageBuilder builder = new MessageBuilder();
        builder.header("From", "Alex <alex@example.org");
        builder.header("To", FROM_ADDRESS);
        builder.header("Subject", "A " + SUBJECT_PART + " Multipart Mail");
        builder.header("Date", "Thu, 14 Feb 2008 12:00:00 +0000 (GMT)");
        builder.body(Charset.forName("us-ascii").encode(BODY).array());
        row = builder.build();
        
        Iterator<MailboxMessage> messages = null;
        SearchQuery query = null; 
        TextExtractor textExtractor = null;
        MailboxSession session = null;
        AttachmentContentLoader attachmentContentLoader = null;
        messageSearches = new MessageSearches(messages, query, textExtractor, attachmentContentLoader, session);
    }


    @Test
    void testBodyShouldMatchPhraseInBody() throws Exception {
        assertThat(messageSearches.isMatch(bodyContains(CUSTARD), row)).isTrue();
        assertThat(messageSearches.isMatch(bodyContains(CUSTARD + CUSTARD), row)).isFalse();
    }

    @Test
    void testBodyMatchShouldBeCaseInsensitive() throws Exception {
        assertThat(messageSearches.isMatch(bodyContains(RHUBARD), row)).isTrue();
        assertThat(messageSearches.isMatch(bodyContains(RHUBARD.toLowerCase(Locale.US)), row)).isTrue();
        assertThat(messageSearches.isMatch(bodyContains(RHUBARD.toLowerCase(Locale.US)), row)).isTrue();
    }

    @Test
    void testBodyShouldNotMatchPhraseOnlyInHeader() throws Exception {
        assertThat(messageSearches.isMatch(bodyContains(FROM_ADDRESS), row)).isFalse();
        assertThat(messageSearches.isMatch(bodyContains(SUBJECT_PART), row)).isFalse();
    }

    @Test
    void testTextShouldMatchPhraseInBody() throws Exception {
        assertThat(messageSearches.isMatch(mailContains(CUSTARD), row)).isTrue();
        assertThat(messageSearches.isMatch(mailContains(CUSTARD + CUSTARD), row)).isFalse();
    }

    @Test
    void testTextMatchShouldBeCaseInsensitive() throws Exception {
        assertThat(messageSearches.isMatch(mailContains(RHUBARD), row)).isTrue();
        assertThat(messageSearches.isMatch(mailContains(RHUBARD.toLowerCase(Locale.US)), row)).isTrue();
        assertThat(messageSearches.isMatch(mailContains(RHUBARD.toLowerCase(Locale.US)), row)).isTrue();
    }

    @Test
    void testBodyShouldMatchPhraseOnlyInHeader() throws Exception {
        assertThat(messageSearches.isMatch(mailContains(FROM_ADDRESS), row)).isTrue();
        assertThat(messageSearches.isMatch(mailContains(SUBJECT_PART), row)).isTrue();
    }
}
