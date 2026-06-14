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

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.ForbiddenDelegationException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.UserDoesNotExistException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.util.AuditTrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

public abstract class AbstractAuthProcessor<R extends ImapRequest> extends AbstractMailboxProcessor<R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAuthProcessor.class);

    private static final String ATTRIBUTE_NUMBER_OF_FAILURES = "org.apache.james.imap.processor.imap4rev1.NUMBER_OF_FAILURES";

    // TODO: this should be configurable
    private static final int MAX_FAILURES = 3;
    private ImapConfiguration imapConfiguration;

    private final PathConverter.Factory pathConverterFactory;

    @FunctionalInterface
    protected interface MailboxSessionAuthWithDelegationSupplier {
        MailboxSession get() throws MailboxException;
    }
    
    public AbstractAuthProcessor(Class<R> acceptableClass, MailboxManager mailboxManager, StatusResponseFactory factory,
                                 MetricFactory metricFactory, PathConverter.Factory pathConverterFactory) {
        super(acceptableClass, mailboxManager, factory, metricFactory);
        this.pathConverterFactory = pathConverterFactory;
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {
        super.configure(imapConfiguration);

        this.imapConfiguration = imapConfiguration;
    }

    /**
     * Turns a terminal SASL step into a protocol response: establishes a session on success (handling delegation),
     * or maps a typed failure to the right IMAP response. Shared by AUTHENTICATE and LOGIN.
     */
    protected void handleSaslStep(SaslStep step, ImapSession session, ImapRequest request, Responder responder, String successLog) {
        if (step instanceof SaslStep.Success success) {
            handleSaslSuccess(session, request, responder, success.identity(), successLog);
        } else if (step instanceof SaslStep.Failure failure) {
            handleSaslFailure(failure, session, request, responder);
        }
    }

    protected void handleSaslSuccess(ImapSession session, ImapRequest request, Responder responder, SaslIdentity identity, String successLog) {
        // The SASL layer already authenticated and authorized: only establish the session here.
        if (!identity.authenticationId().equals(identity.authorizationId())) {
            doAuthWithDelegation(() -> getMailboxManager()
                    .withExtraAuthorizator(withAdminUsers())
                    .authenticate(identity.authenticationId())
                    .as(identity.authorizationId()),
                session, request, responder, identity.authenticationId(), identity.authorizationId());
            return;
        }
        try {
            authSuccess(session, getMailboxManager().authenticate(identity.authenticationId()).withoutDelegation(),
                request, responder, successLog);
        } catch (MailboxException e) {
            LOGGER.error("Session establishment failed after a successful SASL exchange", e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    protected void handleSaslFailure(SaslStep.Failure failure, ImapSession session, ImapRequest request, Responder responder) {
        if (failure.cause() == SaslStep.Failure.Cause.SERVER_ERROR) {
            LOGGER.error("Authentication failed: {}", failure.reason());
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
            return;
        }
        authFailure(session, request, responder, failureResponseText(failure.cause()),
            failure.authenticationId(), failure.authorizationId(), failure.reason());
    }

    private HumanReadableText failureResponseText(SaslStep.Failure.Cause cause) {
        return switch (cause) {
            case INVALID_CREDENTIALS -> HumanReadableText.INVALID_CREDENTIALS;
            case DELEGATION_FORBIDDEN -> HumanReadableText.DELEGATION_FORBIDDEN;
            case UNKNOWN_USER -> HumanReadableText.USER_DOES_NOT_EXIST;
            case MALFORMED_COMMAND, AUTHENTICATION_FAILED, SERVER_ERROR -> HumanReadableText.AUTHENTICATION_FAILED;
        };
    }

    protected Authorizator withAdminUsers() {
        return (userId, otherUserId) -> {
            if (imapConfiguration.getAdminUsers().contains(userId.asString())) {
                return Authorizator.AuthorizationState.ALLOWED;
            }
            return Authorizator.AuthorizationState.FORBIDDEN;
        };
    }

    protected void doAuthWithDelegation(MailboxSessionAuthWithDelegationSupplier mailboxSessionSupplier,
                                        ImapSession session, ImapRequest request, Responder responder,
                                        Username authenticateUser, Username delegatorUser) {
        try {
            authSuccess(session, mailboxSessionSupplier.get(), request, responder, "Authentication with delegation succeeded.");
        } catch (BadCredentialsException e) {
            authFailure(session, request, responder, HumanReadableText.INVALID_CREDENTIALS, Optional.of(authenticateUser),
                Optional.of(delegatorUser), "Password authentication with delegation failed because of bad credentials.");
        } catch (UserDoesNotExistException e) {
            authFailure(session, request, responder, HumanReadableText.USER_DOES_NOT_EXIST, Optional.of(authenticateUser),
                Optional.of(delegatorUser), "Delegation target user does not exist.");
        } catch (ForbiddenDelegationException e) {
            authFailure(session, request, responder, HumanReadableText.DELEGATION_FORBIDDEN, Optional.of(authenticateUser),
                Optional.of(delegatorUser), "Requested delegation is forbidden.");
        } catch (MailboxException e) {
            LOGGER.info("Authentication failed", e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    protected void provisionInbox(ImapSession session, MailboxManager mailboxManager, MailboxSession mailboxSession) throws MailboxException {
        MailboxPath inboxPath = pathConverterFactory.forSession(session).buildFullPath(MailboxConstants.INBOX);
        if (Mono.from(mailboxManager.mailboxExists(inboxPath, mailboxSession)).block()) {
            LOGGER.debug("INBOX exists. No need to create it.");
        } else {
            provisionMailbox(DefaultMailboxes.INBOX, mailboxManager, mailboxSession);
            if (imapConfiguration.isProvisionDefaultMailboxes()) {
                for (String mailbox : DefaultMailboxes.DEFAULT_MAILBOXES) {
                    provisionMailbox(mailbox, mailboxManager, mailboxSession);
                }
            }
        }
    }

    private void provisionMailbox(String mailbox, MailboxManager mailboxManager,
                                  MailboxSession mailboxSession) throws MailboxException {
        MailboxPath mailboxPath = pathConverterFactory.forSession(mailboxSession).buildFullPath(mailbox);
        if (Mono.from(mailboxManager.mailboxExists(mailboxPath, mailboxSession)).block()) {
            LOGGER.debug("{} exists. No need to create it.", mailbox);
            return;
        }
        try {
            mailboxManager.createMailbox(mailboxPath, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, mailboxSession)
                    .ifPresentOrElse(id -> LOGGER.info("Provisioning mailbox {}. {} created.", mailbox, id),
                                     () -> LOGGER.warn(
                                             "Provisioning mailbox {} successful. But no MailboxId have been returned.",
                                             mailbox));
        } catch (MailboxExistsException e) {
            LOGGER.warn("Mailbox {} created by concurrent call. Safe to ignore this exception.", mailbox);
        }
    }

    protected void manageFailureCount(ImapSession session, ImapRequest request, Responder responder, HumanReadableText failed) {
        Integer currentNumberOfFailures = (Integer) session.getAttribute(ATTRIBUTE_NUMBER_OF_FAILURES);
        int failures;
        if (currentNumberOfFailures == null) {
            failures = 1;
        } else {
            failures = currentNumberOfFailures + 1;
        }
        if (failures < MAX_FAILURES) {
            session.setAttribute(ATTRIBUTE_NUMBER_OF_FAILURES, failures);
            no(request, responder, failed);
        } else {
            LOGGER.info("Too many authentication failures. Closing connection.");
            bye(responder, HumanReadableText.TOO_MANY_FAILURES);
            session.logout().block();
        }
    }

    protected void authSuccess(ImapSession session, MailboxSession mailboxSession, ImapRequest request, Responder responder, String log) {
        session.authenticated();
        session.setMailboxSession(mailboxSession);
        try {
            provisionInbox(session, getMailboxManager(), mailboxSession);
        } catch (MailboxException e) {
            LOGGER.error("Provisioning mailboxes failed but authentication continues", e);
        }

        AuditTrail.Entry entry = AuditTrail.entry()
            .username(() -> mailboxSession.getUser().asString())
            .sessionId(() -> session.sessionId().asString())
            .protocol("IMAP")
            .action("AUTH")
            .remoteIP(() -> Optional.ofNullable(session.getRemoteAddress()));
        Optional<Username> assumedUser = mailboxSession.getLoggedInUser();
        if (assumedUser.isPresent()) {
            entry = entry.parameters(() -> ImmutableMap.of("delegatorUser", assumedUser.get().asString()));
        }
        entry.log(log);
        okComplete(request, responder);
        session.stopDetectingCommandInjection();
    }

    protected void authFailure(ImapSession session, ImapRequest request, Responder responder, HumanReadableText failed, Optional<Username> username, Optional<Username> assumedUser, String log) {
        AuditTrail.Entry entry = AuditTrail.entry()
            .username(() -> username.map(name -> name.asString()).orElse(null))
            .sessionId(() -> session.sessionId().asString())
            .protocol("IMAP")
            .action("AUTH")
            .remoteIP(() -> Optional.ofNullable(session.getRemoteAddress()));
        if (assumedUser.isPresent()) {
            entry = entry.parameters(() -> ImmutableMap.of("delegatorUser", assumedUser.get().asString()));
        }
        entry.log(log);
        manageFailureCount(session, request, responder, failed);
    }

}
