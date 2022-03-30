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

package org.apache.james.imap.processor;

import static org.apache.james.imap.api.ImapConstants.SUPPORTS_UIDPLUS;

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.ExpungeRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData;
import org.apache.james.mailbox.MessageManager.MailboxMetaData.FetchGroup;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class ExpungeProcessor extends AbstractMailboxProcessor<ExpungeRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpungeProcessor.class);

    private static final List<Capability> UIDPLUS = ImmutableList.of(SUPPORTS_UIDPLUS);

    public ExpungeProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(ExpungeRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void processRequest(ExpungeRequest request, ImapSession session, Responder responder) {
        try {
            MessageManager mailbox = getSelectedMailbox(session)
                .orElseThrow(() -> new MailboxException("Session not in SELECTED state"));
            MailboxSession mailboxSession = session.getMailboxSession();

            if (!getMailboxManager().hasRight(mailbox.getMailboxEntity(), MailboxACL.Right.PerformExpunge, mailboxSession)) {
                no(request, responder, HumanReadableText.MAILBOX_IS_READ_ONLY);
            } else {
                int expunged = expunge(request, session, mailbox, mailboxSession);
                unsolicitedResponses(session, responder, false).block();
                respondOk(request, session, responder, mailbox, mailboxSession, expunged);
            }
        } catch (MessageRangeException e) {
            LOGGER.debug("Expunge failed", e);
            taggedBad(request, responder, HumanReadableText.INVALID_MESSAGESET);
        } catch (MailboxException e) {
            LOGGER.error("Expunge failed for mailbox {}", session.getSelected().getMailboxId(), e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    private int expunge(ExpungeRequest request, ImapSession session, MessageManager mailbox, MailboxSession mailboxSession) throws MailboxException {
        IdRange[] ranges = request.getUidSet();
        if (ranges == null) {
           return expunge(mailbox, MessageRange.all(), session, mailboxSession);
        } else {
            int expunged = 0;
            // Handle UID EXPUNGE which is part of UIDPLUS
            // See http://tools.ietf.org/html/rfc4315
            for (IdRange range : ranges) {
                MessageRange mRange = messageRange(session.getSelected(), range, true);
                if (mRange != null) {
                    expunged += expunge(mailbox, mRange, session, mailboxSession);
                }
            }
            return expunged;
        }
    }

    private void respondOk(ExpungeRequest request, ImapSession session, Responder responder, MessageManager mailbox, MailboxSession mailboxSession, int expunged) throws MailboxException {
        // Check if QRESYNC was enabled and at least one message was expunged. If so we need to respond with an OK response that contain the HIGHESTMODSEQ
        //
        // See RFC5162 3.3 EXPUNGE Command 3.5. UID EXPUNGE Command
        if (EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC)  && expunged > 0) {
            MailboxMetaData mdata = mailbox.getMetaData(false, mailboxSession, FetchGroup.NO_COUNT);
            okComplete(request, ResponseCode.highestModSeq(mdata.getHighestModSeq()), responder);
        } else {
            okComplete(request, responder);
        }
    }

    private int expunge(MessageManager mailbox, MessageRange range, ImapSession session, MailboxSession mailboxSession) throws MailboxException {
        final Iterator<MessageUid> it = mailbox.expunge(range, mailboxSession);
        final SelectedMailbox selected = session.getSelected();
        int expunged = 0;
        if (mailboxSession != null) {
            while (it.hasNext()) {
                final MessageUid uid = it.next();
                selected.removeRecent(uid);
                expunged++;
            }
        }
        return expunged;
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return UIDPLUS;
    }

    @Override
    protected Closeable addContextToMDC(ExpungeRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "EXPUNGE")
            .addToContext("uidSet", IdRange.toString(request.getUidSet()))
            .build();
    }
}
