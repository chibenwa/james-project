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

package org.apache.james.protocols.api.sasl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.core.Username;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.junit.jupiter.api.Test;

class PlainSaslMechanismTest {
    private static final Username AUTHENTICATION_ID = Username.of("user@example.com");
    private static final Username AUTHORIZATION_ID = Username.of("delegated@example.com");
    private static final Username UNKNOWN_ID = Username.of("unknown@example.com");
    private static final String PASSWORD = "secret";

    private static final Authenticator AUTHENTICATOR = (userid, passwd) ->
        Optional.of(userid)
            .filter(AUTHENTICATION_ID::equals)
            .filter(any -> PASSWORD.contentEquals(passwd));

    private static final Authorizator AUTHORIZATOR = (userId, otherUserId) -> {
        if (UNKNOWN_ID.equals(otherUserId)) {
            return Authorizator.AuthorizationState.UNKNOWN_USER;
        }
        if (AUTHENTICATION_ID.equals(userId) && AUTHORIZATION_ID.equals(otherUserId)) {
            return Authorizator.AuthorizationState.ALLOWED;
        }
        return Authorizator.AuthorizationState.FORBIDDEN;
    };

    private final PlainSaslMechanism testee = new PlainSaslMechanism(AUTHENTICATOR, AUTHORIZATOR, false);

    @Test
    void shouldChallengeWhenNoInitialResponse() {
        // GIVEN a PLAIN exchange without SASL-IR
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME, Optional.empty());

        // WHEN the mechanism starts
        SaslStep firstStep = testee.start(request).firstStep();

        // THEN the server asks for one client response
        assertThat(firstStep).isEqualTo(new SaslStep.Challenge(Optional.empty()));
    }

    @Test
    void shouldSucceedForInitialResponseWithoutDelegation() {
        // GIVEN a valid PLAIN initial response without an authorization identity
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        // WHEN the mechanism consumes the initial response
        SaslStep step = testee.start(request).firstStep();

        // THEN the embedded verification succeeds with a same-user identity
        assertThat(step).isEqualTo(new SaslStep.Success(
            new SaslIdentity(AUTHENTICATION_ID, AUTHENTICATION_ID), Optional.empty()));
    }

    @Test
    void shouldSucceedForContinuationResponseWithAuthorizedDelegation() {
        // GIVEN a PLAIN exchange waiting for the client response
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME, Optional.empty());
        SaslExchange exchange = testee.start(request);

        // WHEN the client sends a response with an authorization identity the user may impersonate
        SaslStep step = exchange.onResponse(bytes(AUTHORIZATION_ID.asString() + "\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD));

        // THEN both identities are preserved for protocol-level session establishment
        assertThat(step).isEqualTo(new SaslStep.Success(
            new SaslIdentity(AUTHENTICATION_ID, AUTHORIZATION_ID), Optional.empty()));
    }

    @Test
    void shouldAcceptTwoPartResponseWithoutAuthorizationIdentity() {
        // GIVEN a PLAIN response encoded as authcid/password
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes(AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        // WHEN the mechanism consumes the response
        SaslStep step = testee.start(request).firstStep();

        // THEN it treats the response as a non-delegated authentication
        assertThat(step).isEqualTo(new SaslStep.Success(
            new SaslIdentity(AUTHENTICATION_ID, AUTHENTICATION_ID), Optional.empty()));
    }

    @Test
    void shouldFailWithInvalidCredentialsWhenPasswordDoesNotMatch() {
        // GIVEN a well-formed PLAIN response carrying a wrong password
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0wrong")));

        // WHEN the mechanism consumes the response
        SaslStep step = testee.start(request).firstStep();

        // THEN verification fails inside the SASL layer with the attempted identity preserved for auditing
        assertThat(step).isEqualTo(new SaslStep.Failure("Password authentication failed because of bad credentials.",
            SaslStep.Failure.Cause.INVALID_CREDENTIALS, Optional.of(AUTHENTICATION_ID), Optional.empty()));
    }

    @Test
    void shouldFailWhenDelegationIsForbidden() {
        // GIVEN a valid password but a forbidden authorization identity
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("other@example.com\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        // WHEN the mechanism consumes the response
        SaslStep step = testee.start(request).firstStep();

        // THEN the authorization step fails inside the SASL layer
        assertThat(step).isEqualTo(new SaslStep.Failure("Requested delegation is forbidden.",
            SaslStep.Failure.Cause.DELEGATION_FORBIDDEN,
            Optional.of(AUTHENTICATION_ID), Optional.of(Username.of("other@example.com"))));
    }

    @Test
    void shouldFailWhenDelegationTargetDoesNotExist() {
        // GIVEN a valid password but an unknown authorization identity
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes(UNKNOWN_ID.asString() + "\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        // WHEN the mechanism consumes the response
        SaslStep step = testee.start(request).firstStep();

        // THEN the authorization step reports the unknown delegation target
        assertThat(step).isEqualTo(new SaslStep.Failure("Delegation target user does not exist.",
            SaslStep.Failure.Cause.UNKNOWN_USER,
            Optional.of(AUTHENTICATION_ID), Optional.of(UNKNOWN_ID)));
    }

    @Test
    void shouldUseCanonicalUsernameReturnedByAuthenticator() {
        // GIVEN an authenticator normalizing the supplied login
        Username canonical = Username.of("canonical@example.com");
        PlainSaslMechanism mechanism = new PlainSaslMechanism((userid, passwd) -> Optional.of(canonical), AUTHORIZATOR, false);
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0alias\0" + PASSWORD)));

        // WHEN the mechanism consumes the response
        SaslStep step = mechanism.start(request).firstStep();

        // THEN the success identity carries the canonical user
        assertThat(step).isEqualTo(new SaslStep.Success(
            new SaslIdentity(canonical, canonical), Optional.empty()));
    }

    @Test
    void configuredAdminUsersShouldAllowDelegation() {
        // GIVEN a server configuration declaring the authenticated user as admin
        BaseHierarchicalConfiguration serverConfiguration = new BaseHierarchicalConfiguration();
        serverConfiguration.addProperty("auth.adminUsers.adminUser", AUTHENTICATION_ID.asString());
        Authorizator forbiddingAuthorizator = (userId, otherUserId) -> Authorizator.AuthorizationState.FORBIDDEN;
        PlainSaslMechanism mechanism = new PlainSaslMechanism(AUTHENTICATOR,
            SaslDelegation.withConfiguredAdminUsers(forbiddingAuthorizator, serverConfiguration), false);
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("other@example.com\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        // WHEN the admin authenticates with an authorization identity rejected by the base authorizator
        SaslStep step = mechanism.start(request).firstStep();

        // THEN the configured admin users still allow the delegation
        assertThat(step).isEqualTo(new SaslStep.Success(
            new SaslIdentity(AUTHENTICATION_ID, Username.of("other@example.com")), Optional.empty()));
    }

    @Test
    void shouldFailMalformedResponse() {
        // GIVEN a PLAIN response without the expected separators
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("missing-separators")));

        // WHEN the mechanism consumes the response
        SaslStep step = testee.start(request).firstStep();

        // THEN it fails before any credential verification
        assertThat(step).isEqualTo(new SaslStep.Failure("Malformed authentication command.",
            SaslStep.Failure.Cause.MALFORMED_COMMAND, Optional.empty(), Optional.empty()));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
