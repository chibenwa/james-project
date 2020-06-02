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

import static org.apache.james.mailbox.model.SearchQuery.address;
import static org.apache.james.mailbox.model.SearchQuery.all;
import static org.apache.james.mailbox.model.SearchQuery.flagIsSet;
import static org.apache.james.mailbox.model.SearchQuery.flagIsUnSet;
import static org.apache.james.mailbox.model.SearchQuery.headerContains;
import static org.apache.james.mailbox.model.SearchQuery.headerDateAfter;
import static org.apache.james.mailbox.model.SearchQuery.headerDateBefore;
import static org.apache.james.mailbox.model.SearchQuery.headerDateOn;
import static org.apache.james.mailbox.model.SearchQuery.headerExists;
import static org.apache.james.mailbox.model.SearchQuery.internalDateAfter;
import static org.apache.james.mailbox.model.SearchQuery.internalDateBefore;
import static org.apache.james.mailbox.model.SearchQuery.internalDateOn;
import static org.apache.james.mailbox.model.SearchQuery.not;
import static org.apache.james.mailbox.model.SearchQuery.or;
import static org.apache.james.mailbox.model.SearchQuery.sizeEquals;
import static org.apache.james.mailbox.model.SearchQuery.sizeGreaterThan;
import static org.apache.james.mailbox.model.SearchQuery.sizeLessThan;
import static org.apache.james.mailbox.model.SearchQuery.uid;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import javax.mail.Flags;

import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.store.MessageBuilder;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchUtilsTest {
    static final String RHUBARD = "Rhubard";

    static final String CUSTARD = "Custard";

    static final Date SUN_SEP_9TH_2001 = new Date(1000000000000L);

    static final int SIZE = 1729;

    static final String DATE_FIELD = "Date";

    static final String SUBJECT_FIELD = "Subject";

    static final String RFC822_SUN_SEP_9TH_2001 = "Sun, 9 Sep 2001 09:10:48 +0000 (GMT)";

    static final String TEXT = RHUBARD + RHUBARD + RHUBARD;

    MessageBuilder builder;

    MessageSearches messageSearches;
    
    Calendar getGMT() {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.UK);
    }
    
    Date getDate(int day, int month, int year) {
        Calendar cal = getGMT();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }
    
    @BeforeEach
    void setUp() {
        builder = new MessageBuilder()
            .uid(MessageUid.of(1009));
        
        Iterator<MailboxMessage> messages = null;
        SearchQuery query = null; 
        TextExtractor textExtractor = null;
        MailboxSession session = null;

        AttachmentContentLoader attachmentContentLoader = null;
        messageSearches = new MessageSearches(messages, query, textExtractor, attachmentContentLoader, session);
    }
    
    @Test
    void testMatchSizeLessThan() throws Exception {
        builder.size(SIZE);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(sizeLessThan(SIZE - 1), row)).isFalse();
        assertThat(messageSearches.isMatch(sizeLessThan(SIZE), row)).isFalse();
        assertThat(messageSearches.isMatch(sizeLessThan(SIZE + 1), row)).isTrue();
        assertThat(messageSearches.isMatch(sizeLessThan(Integer.MAX_VALUE), row)).isTrue();
    }

    @Test
    void testMatchSizeMoreThan() throws Exception {
        builder.size(SIZE);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(sizeGreaterThan(SIZE - 1), row)).isTrue();
        assertThat(messageSearches.isMatch(sizeGreaterThan(SIZE), row)).isFalse();
        assertThat(messageSearches.isMatch(sizeGreaterThan(SIZE + 1), row)).isFalse();
        assertThat(messageSearches.isMatch(sizeGreaterThan(Integer.MAX_VALUE), row)).isFalse();
    }

    @Test
    void testMatchSizeEquals() throws Exception {
        builder.size(SIZE);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(sizeEquals(SIZE - 1), row)).isFalse();
        assertThat(messageSearches.isMatch(sizeEquals(SIZE), row)).isTrue();
        assertThat(messageSearches.isMatch(sizeEquals(SIZE + 1), row)).isFalse();
        assertThat(messageSearches.isMatch(sizeEquals(Integer.MAX_VALUE), row)).isFalse();
    }

    @Test
    void testMatchInternalDateEquals() throws Exception {
        builder.internalDate(SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(internalDateOn(getDate(9, 9, 2000), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(internalDateOn(getDate(8, 9, 2001), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(internalDateOn(getDate(9, 9, 2001), DateResolution.Day), row)).isTrue();
        assertThat(messageSearches.isMatch(internalDateOn(getDate(10, 9, 2001), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(internalDateOn(getDate(9, 9, 2002), DateResolution.Day),
            row)).isFalse();
    }

    
    @Test
    void testMatchInternalDateBefore() throws Exception {
        builder.internalDate(SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(internalDateBefore(getDate(9, 9, 2000), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(internalDateBefore(getDate(8, 9, 2001), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(internalDateBefore(getDate(9, 9, 2001), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(internalDateBefore(getDate(10, 9, 2001), DateResolution.Day), row)).isTrue();
        assertThat(messageSearches.isMatch(internalDateBefore(getDate(9, 9, 2002), DateResolution.Day), row)).isTrue();
    }

    @Test
    void testMatchInternalDateAfter() throws Exception {
        builder.internalDate(SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(internalDateAfter(getDate(9, 9, 2000), DateResolution.Day), row)).isTrue();
        assertThat(messageSearches.isMatch(internalDateAfter(getDate(8, 9, 2001), DateResolution.Day), row)).isTrue();
        assertThat(messageSearches.isMatch(internalDateAfter(getDate(9, 9, 2001), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(internalDateAfter(getDate(10, 9, 2001), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(internalDateAfter(getDate(9, 9, 2002), DateResolution.Day), row)).isFalse();
    }

    @Test
    void testMatchHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD, RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001), DateResolution.Day), row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row)).isFalse();
    }

    @Test
    void testShouldMatchCapsHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD.toUpperCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001), DateResolution.Day), row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row)).isFalse();
    }

    @Test
    void testShouldMatchLowersHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001),DateResolution.Day), row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row)).isFalse();
    }

    @Test
    void testMatchHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD, RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day), row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row)).isFalse();
    }

    @Test
    void testShouldMatchCapsHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD.toUpperCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row)).isFalse();
    }

    @Test
    void testShouldMatchLowersHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row)).isFalse();
    }

    @Test
    void testMatchHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row)).isFalse();
    }

    @Test
    void testShouldMatchCapsHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row)).isFalse();
    }

    @Test
    void testShouldMatchLowersHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row)).isFalse();
    }

    @Test
    void testMatchHeaderContainsCaps() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase(Locale.US));
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerContains(DATE_FIELD, CUSTARD), row)).isFalse();
        assertThat(messageSearches.isMatch(headerContains(DATE_FIELD, TEXT), row)).isFalse();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, TEXT), row)).isTrue();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, RHUBARD), row)).isTrue();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, CUSTARD), row)).isFalse();
    }

    @Test
    void testMatchHeaderContainsLowers() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase(Locale.US));
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerContains(DATE_FIELD, CUSTARD), row)).isFalse();
        assertThat(messageSearches.isMatch(headerContains(DATE_FIELD, TEXT), row)).isFalse();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, TEXT), row)).isTrue();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, RHUBARD), row)).isTrue();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, CUSTARD), row)).isFalse();
    }

    @Test
    void testMatchHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase(Locale.US));
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerContains(DATE_FIELD, CUSTARD), row)).isFalse();
        assertThat(messageSearches.isMatch(headerContains(DATE_FIELD, TEXT), row)).isFalse();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, TEXT), row)).isTrue();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, RHUBARD), row)).isTrue();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, CUSTARD), row)).isFalse();
    }

    @Test
    void testShouldMatchLowerHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerContains(DATE_FIELD, CUSTARD), row)).isFalse();
        assertThat(messageSearches.isMatch(headerContains(DATE_FIELD, TEXT), row)).isFalse();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, TEXT), row)).isTrue();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, RHUBARD), row)).isTrue();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, CUSTARD), row)).isFalse();
    }

    @Test
    void testShouldMatchCapsHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD.toUpperCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerContains(DATE_FIELD, CUSTARD), row)).isFalse();
        assertThat(messageSearches.isMatch(headerContains(DATE_FIELD, TEXT), row)).isFalse();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, TEXT), row)).isTrue();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, RHUBARD), row)).isTrue();
        assertThat(messageSearches.isMatch(headerContains(SUBJECT_FIELD, CUSTARD), row)).isFalse();
    }

    @Test
    void testMatchHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerExists(DATE_FIELD), row)).isFalse();
        assertThat(messageSearches.isMatch(headerExists(SUBJECT_FIELD), row)).isTrue();
    }

    @Test
    void testShouldMatchLowersHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerExists(DATE_FIELD), row)).isFalse();
        assertThat(messageSearches.isMatch(headerExists(SUBJECT_FIELD), row)).isTrue();
    }

    @Test
    void testShouldMatchUppersHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerExists(DATE_FIELD), row)).isFalse();
        assertThat(messageSearches.isMatch(headerExists(SUBJECT_FIELD), row)).isTrue();
    }

    @Test
    void testShouldMatchUidRange() throws Exception {
        builder.setKey(1, MessageUid.of(1729));
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1), MessageUid.of(1))), row)).isFalse();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1728), MessageUid.of(1728))), row)).isFalse();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1729), MessageUid.of(1729))), row)).isTrue();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1730), MessageUid.of(1730))), row)).isFalse();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1), MessageUid.of(1728))), row)).isFalse();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1), MessageUid.of(1729))), row)).isTrue();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1729), MessageUid.of(1800))), row)).isTrue();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1730), MessageUid.MAX_VALUE)), row)).isFalse();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1730), MessageUid.MAX_VALUE, MessageUid.of(1), MessageUid.of(1728))), row)).isFalse();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1730), MessageUid.MAX_VALUE, MessageUid.of(1), MessageUid.of(1729))), row)).isTrue();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1), MessageUid.of(1728), MessageUid.of(1800), MessageUid.of(1810))), row)).isFalse();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1), MessageUid.of(1), MessageUid.of(1729), MessageUid.of(1729))), row)).isTrue();
        assertThat(messageSearches.isMatch(uid(range(MessageUid.of(1), MessageUid.of(1), MessageUid.of(1800), MessageUid.of(1800))), row)).isFalse();
    }

    @Test
    void testShouldMatchSeenFlagSet() throws Exception {
        builder.setFlags(true, false, false, false, false, false);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.SEEN), row)).isTrue();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.FLAGGED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.ANSWERED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DRAFT), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DELETED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.RECENT), row)).isFalse();
    }

    @Test
    void testShouldMatchAnsweredFlagSet() throws Exception {
        builder.setFlags(false, false, true, false, false, false);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.SEEN), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.FLAGGED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.ANSWERED), row)).isTrue();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DRAFT), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DELETED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.RECENT), row)).isFalse();
    }

    @Test
    void testShouldMatchFlaggedFlagSet() throws Exception {
        builder.setFlags(false, true, false, false, false, false);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.SEEN), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.FLAGGED), row)).isTrue();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.ANSWERED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DRAFT), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DELETED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.RECENT), row)).isFalse();
    }

    @Test
    void testShouldMatchDraftFlagSet() throws Exception {
        builder.setFlags(false, false, false, true, false, false);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.SEEN), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.FLAGGED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.ANSWERED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DRAFT), row)).isTrue();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DELETED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.RECENT), row)).isFalse();
    }

    
    @Test
    void testShouldMatchDeletedFlagSet() throws Exception {
        builder.setFlags(false, false, false, false, true, false);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.SEEN), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.FLAGGED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.ANSWERED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DRAFT), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DELETED), row)).isTrue();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.RECENT), row)).isFalse();
    }

    @Test
    void testShouldMatchSeenRecentSet() throws Exception {
        builder.setFlags(false, false, false, false, false, true);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.SEEN), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.FLAGGED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.ANSWERED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DRAFT), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.DELETED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsSet(Flags.Flag.RECENT), row)).isTrue();
    }

    @Test
    void testShouldMatchSeenFlagUnSet() throws Exception {
        builder.setFlags(false, true, true, true, true, true);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.SEEN), row)).isTrue();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.FLAGGED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.ANSWERED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DRAFT), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DELETED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.RECENT), row)).isFalse();
    }

    @Test
    void testShouldMatchAnsweredFlagUnSet() throws Exception {
        builder.setFlags(true, true, false, true, true, true);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.SEEN), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.FLAGGED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.ANSWERED), row)).isTrue();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DRAFT), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DELETED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.RECENT), row)).isFalse();
    }

    @Test
    void testShouldMatchFlaggedFlagUnSet() throws Exception {
        builder.setFlags(true, false, true, true, true, true);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.SEEN), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.FLAGGED), row)).isTrue();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.ANSWERED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DRAFT), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DELETED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.RECENT), row)).isFalse();
    }

    @Test
    void testShouldMatchDraftFlagUnSet() throws Exception {
        builder.setFlags(true, true, true, false, true, true);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.SEEN), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.FLAGGED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.ANSWERED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DRAFT), row)).isTrue();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DELETED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.RECENT), row)).isFalse();
    }

    @Test
    void testShouldMatchDeletedFlagUnSet() throws Exception {
        builder.setFlags(true, true, true, true, false, true);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.SEEN), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.FLAGGED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.ANSWERED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DRAFT), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DELETED), row)).isTrue();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.RECENT), row)).isFalse();
    }

    @Test
    void testShouldMatchSeenRecentUnSet() throws Exception {
        builder.setFlags(true, true, true, true, true, false);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.SEEN), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.FLAGGED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.ANSWERED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DRAFT), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.DELETED), row)).isFalse();
        assertThat(messageSearches.isMatch(flagIsUnSet(Flags.Flag.RECENT), row)).isTrue();
    }

    @Test
    void testShouldMatchAll() throws Exception {
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(all(), row)).isTrue();
    }

    @Test
    void testShouldMatchNot() throws Exception {
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(not(all()), row)).isFalse();
        assertThat(messageSearches.isMatch(not(headerExists(DATE_FIELD)), row)).isTrue();
    }

    @Test
    void testShouldMatchOr() throws Exception {
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(or(all(), headerExists(DATE_FIELD)), row)).isTrue();
        assertThat(messageSearches.isMatch(or(headerExists(DATE_FIELD), all()), row)).isTrue();
        assertThat(messageSearches.isMatch(or(headerExists(DATE_FIELD), headerExists(DATE_FIELD)), row)).isFalse();
        assertThat(messageSearches.isMatch(or(all(), all()), row)).isTrue();
    }

    @Test
    void testShouldMatchAnd() throws Exception {
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.and(all(), headerExists(DATE_FIELD)), row)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.and(headerExists(DATE_FIELD), all()), row)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.and(headerExists(DATE_FIELD), headerExists(DATE_FIELD)), row)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.and(all(), all()), row)).isTrue();
    }
    
    SearchQuery.UidRange[] range(MessageUid low, MessageUid high) {
        return new SearchQuery.UidRange[]{ new SearchQuery.UidRange(low, high) };
    }

    SearchQuery.UidRange[] range(MessageUid lowOne, MessageUid highOne,
            MessageUid lowTwo, MessageUid highTwo) {
        return new SearchQuery.UidRange[]{
                new SearchQuery.UidRange(lowOne, highOne),
                new SearchQuery.UidRange(lowTwo, highTwo) };
    }
    
    
    @Test
    void testMatchHeaderDateOnWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 00:00:00 +0300");
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD,
            getDate(26, 3, 2007), DateResolution.Day),row)).isTrue();
        
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD,
            getDate(25, 3, 2007), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateOn(DATE_FIELD,
            getDate(27, 3, 2007), DateResolution.Day),row)).isFalse();
    }
    

    @Test
    void testShouldMatchHeaderDateBeforeWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 00:00:00 +0300");
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
            getDate(26, 3, 2007), DateResolution.Day),row)).isFalse();
        
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
            getDate(27, 3, 2007), DateResolution.Day),row)).isTrue();
        assertThat(messageSearches.isMatch(headerDateBefore(DATE_FIELD,
            getDate(25, 3, 2007), DateResolution.Day),row)).isFalse();
    }

    @Test
    void testShouldMatchHeaderDateAfterWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 00:00:00 +0300");
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(26, 3,
                2007), DateResolution.Day),row)).isFalse();
        
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(27, 3,
                2007), DateResolution.Day),row)).isFalse();
        assertThat(messageSearches.isMatch(headerDateAfter(DATE_FIELD, getDate(25, 3,
                2007), DateResolution.Day),row)).isTrue();
    }
    
    @Test
    void testShouldMatchAddressHeaderWithComments() throws Exception {
        builder.header("To", "<user-from (comment)@ (comment) domain.org>");
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(address(AddressType.To, "user-from@domain.org"), row)).isTrue();
        assertThat(messageSearches.isMatch(address(AddressType.From, "user-from@domain.org"), row)).isFalse();
    }
}
