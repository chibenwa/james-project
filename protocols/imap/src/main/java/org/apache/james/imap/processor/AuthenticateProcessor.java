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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;

import javax.mail.internet.AddressException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.AuthenticateRequest;
import org.apache.james.imap.message.request.IRAuthenticateRequest;
import org.apache.james.imap.message.response.AuthenticateResponse;
import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.OIDCSASLParser;
import org.apache.james.protocols.api.OidcSASLConfiguration;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * Processor which handles the AUTHENTICATE command. Only authtype of PLAIN is supported ATM.
 */
public class AuthenticateProcessor extends AbstractAuthProcessor<AuthenticateRequest> implements CapabilityImplementingProcessor {
    @FunctionalInterface
    public interface DomainPartResolver {
        DomainPartResolver DEFAULT = username -> {
            if (username.hasDomainPart()) {
                return username;
            }
            throw new IllegalArgumentException("No domain part");
        };

        Username resolve(Username username);
    }
    
    public static final String AUTH_PLAIN = "AUTH=PLAIN";
    public static final Capability AUTH_PLAIN_CAPABILITY = Capability.of(AUTH_PLAIN);
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticateProcessor.class);
    private static final String AUTH_TYPE_PLAIN = "PLAIN";
    private static final String AUTH_TYPE_OAUTHBEARER = "OAUTHBEARER";
    private static final String AUTH_TYPE_XOAUTH2 = "XOAUTH2";
    private static final List<Capability> OAUTH_CAPABILITIES = ImmutableList.of(Capability.of("AUTH=" + AUTH_TYPE_OAUTHBEARER), Capability.of("AUTH=" + AUTH_TYPE_XOAUTH2));
    public static final Capability SASL_CAPABILITY = Capability.of("SASL-IR");

    private final Authorizator authorizator;
    private final DomainPartResolver domainPartResolver;

    public AuthenticateProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                                 Authorizator authorizator, DomainPartResolver domainPartResolver, MetricFactory metricFactory) {
        super(AuthenticateRequest.class, mailboxManager, factory, metricFactory);
        this.authorizator = authorizator;
        this.domainPartResolver = domainPartResolver;
    }

    @Override
    protected void processRequest(AuthenticateRequest request, ImapSession session, final Responder responder) {
        final String authType = request.getAuthType();

        if (authType.equalsIgnoreCase(AUTH_TYPE_PLAIN)) {
            // See if AUTH=PLAIN is allowed. See IMAP-304
            if (session.isPlainAuthDisallowed()) {
                no(request, responder, HumanReadableText.DISABLED_LOGIN);
            } else {
                if (request instanceof IRAuthenticateRequest) {
                    IRAuthenticateRequest irRequest = (IRAuthenticateRequest) request;
                    doPlainAuth(irRequest.getInitialClientResponse(), session, request, responder);
                } else {
                    session.executeSafely(() -> {
                        responder.respond(new AuthenticateResponse());
                        session.pushLineHandler((requestSession, data) -> {
                            doPlainAuth(extractInitialClientResponse(data), requestSession, request, responder);
                            // remove the handler now
                            requestSession.popLineHandler();
                        });
                    });
                }
            }
        } else if (authType.equalsIgnoreCase(AUTH_TYPE_OAUTHBEARER) || authType.equalsIgnoreCase(AUTH_TYPE_XOAUTH2)) {
            if (request instanceof IRAuthenticateRequest) {
                IRAuthenticateRequest irRequest = (IRAuthenticateRequest) request;
                doOAuth(irRequest.getInitialClientResponse(), session, request, responder);
            } else {
                session.executeSafely(() -> {
                    responder.respond(new AuthenticateResponse());
                    session.pushLineHandler((requestSession, data) -> {
                        doOAuth(extractInitialClientResponse(data), requestSession, request, responder);
                        requestSession.popLineHandler();
                    });
                });
            }
        } else {
            LOGGER.debug("Unsupported authentication mechanism '{}'", authType);
            no(request, responder, HumanReadableText.UNSUPPORTED_AUTHENTICATION_MECHANISM);
        }
    }

    /**
     * Parse the initialClientResponse and do a PLAIN AUTH with it
     */
    protected void doPlainAuth(String initialClientResponse, ImapSession session, ImapRequest request, Responder responder) {
        LOGGER.debug("BASE 64: {}", initialClientResponse);
        try {
            String userpass = new String(Base64.getDecoder().decode(initialClientResponse));
            LOGGER.debug("DECODED: {}", initialClientResponse);
            StringTokenizer authTokenizer = new StringTokenizer(userpass, "\0");
            Username target = Username.of(authTokenizer.nextToken());  // Authorization Identity
            LOGGER.debug("TARGET: {}", target.asString());
            Username origin = Username.of(session.extractOuParameterFromClientCertificate()
                .orElseThrow(() -> new RuntimeException("No OU field in the certificate DN provided by the client")));
            LOGGER.debug("ORIGIN: {}", origin.asString());
            Username fullyQualifiedOrigin = domainPartResolver.resolve(origin);
            LOGGER.debug("RESOLVED ORIGIN: {}", fullyQualifiedOrigin.asString());

            if (authorizator.canLoginAsOtherUser(fullyQualifiedOrigin, target).equals(Authorizator.AuthorizationState.ALLOWED)) {
                MailboxSession mailboxSession = getMailboxManager().createSystemSession(target);
                session.authenticated();
                session.setMailboxSession(mailboxSession);
                provisionInbox(session, getMailboxManager(), mailboxSession);
                okComplete(request, responder);
                session.stopDetectingCommandInjection();
            } else {
                LOGGER.info("Delegation issue: {} cannot connect as {}", fullyQualifiedOrigin.asString(), target.asString());
                manageFailureCount(session, request, responder);
            }
        } catch (Exception e) {
            // Ignored - this exception in parsing will be dealt
            // with in the if clause below
            LOGGER.info("Invalid syntax in AUTHENTICATE initial client response", e);
            manageFailureCount(session, request, responder);
        }
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        List<Capability> caps = new ArrayList<>();
        // Only ounce AUTH=PLAIN if the session does allow plain auth or TLS is active.
        // See IMAP-304
        if (!session.isPlainAuthDisallowed()) {
            caps.add(AUTH_PLAIN_CAPABILITY);
        }
        // Support for SASL-IR. See RFC4959
        caps.add(SASL_CAPABILITY);
        if (session.supportsOAuth()) {
            caps.addAll(OAUTH_CAPABILITIES);
        }
        return ImmutableList.copyOf(caps);
    }

    @Override
    protected MDCBuilder mdc(AuthenticateRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "AUTHENTICATE")
            .addToContext("authType", request.getAuthType());
    }

    private void doOAuth(String initialResponse, ImapSession session, ImapRequest request, Responder responder) {
        if (!session.supportsOAuth()) {
            no(request, responder, HumanReadableText.UNSUPPORTED_AUTHENTICATION_MECHANISM);
        } else {
            OIDCSASLParser.parse(initialResponse)
                .flatMap(oidcInitialResponseValue -> session.oidcSaslConfiguration().map(configure -> Pair.of(oidcInitialResponseValue, configure)))
                .ifPresentOrElse(pair -> doOAuth(pair.getLeft(), pair.getRight(), session, request, responder),
                    () -> manageFailureCount(session, request, responder));
        }
        session.stopDetectingCommandInjection();
    }

    private void doOAuth(OIDCSASLParser.OIDCInitialResponse oidcInitialResponse, OidcSASLConfiguration oidcSASLConfiguration,
                         ImapSession session, ImapRequest request, Responder responder) {
        validateToken(oidcSASLConfiguration, oidcInitialResponse.getToken())
            .ifPresentOrElse(authenticatedUser -> {
                Username associatedUser = Username.of(oidcInitialResponse.getAssociatedUser());
                if (!associatedUser.equals(authenticatedUser)) {
                    doAuthWithDelegation(() -> getMailboxManager()
                            .authenticate(authenticatedUser)
                            .as(associatedUser),
                        session, request, responder);
                } else {
                    authSuccess(authenticatedUser, session, request, responder);
                }
            }, () -> manageFailureCount(session, request, responder));
    }

    private Optional<Username> validateToken(OidcSASLConfiguration oidcSASLConfiguration, String token) {
        return Mono.from(OidcJwtTokenVerifier.verifyWithMaybeIntrospection(token,
                oidcSASLConfiguration.getJwksURL(),
                oidcSASLConfiguration.getClaim(),
                oidcSASLConfiguration.getIntrospectionEndpoint()
                    .map(endpoint -> new IntrospectionEndpoint(endpoint, oidcSASLConfiguration.getIntrospectionEndpointAuthorization()))))
            .blockOptional()
            .flatMap(this::extractUserFromClaim);
    }

    private Optional<Username> extractUserFromClaim(String claimValue) {
        try {
            return Optional.of(Username.fromMailAddress(new MailAddress(claimValue)));
        } catch (AddressException e) {
            return Optional.empty();
        }
    }

    private static String extractInitialClientResponse(byte[] data) {
        // cut of the CRLF
        return new String(data, 0, data.length - 2, StandardCharsets.US_ASCII);
    }

}
