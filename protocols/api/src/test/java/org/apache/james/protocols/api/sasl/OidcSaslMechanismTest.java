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
import org.apache.james.mailbox.Authorizator;
import org.junit.jupiter.api.Test;

class OidcSaslMechanismTest {
    private static final Authorizator DENY_ALL = (userId, otherUserId) -> Authorizator.AuthorizationState.FORBIDDEN;
    private static final Username USER = Username.of("user@example.com");
    private static final String TOKEN = "token";

    @Test
    void oauthBearerShouldFailWhenNoOidcConfiguration() {
        // GIVEN a decoded OAUTHBEARER initial response on a server without OIDC configuration
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanism.NAME,
            Optional.of(bytes("n,a=" + USER.asString() + ",\u0001auth=Bearer " + TOKEN + "\u0001\u0001")));

        // WHEN the mechanism consumes the response
        SaslStep step = new OauthBearerSaslMechanism(Optional.empty(), DENY_ALL).start(request).firstStep();

        // THEN verification fails inside the SASL layer with the requested identity preserved for auditing
        assertThat(step).isEqualTo(new SaslStep.Failure("OAuth authentication failed.",
            SaslStep.Failure.Cause.AUTHENTICATION_FAILED, Optional.empty(), Optional.of(USER)));
    }

    @Test
    void xOauth2ShouldFailWhenNoOidcConfiguration() {
        // GIVEN a decoded XOAUTH2 initial response on a server without OIDC configuration
        SaslInitialRequest request = new SaslInitialRequest(XOauth2SaslMechanism.NAME,
            Optional.of(bytes("user=" + USER.asString() + "\u0001auth=Bearer " + TOKEN + "\u0001\u0001")));

        // WHEN the mechanism consumes the response
        SaslStep step = new XOauth2SaslMechanism(Optional.empty(), DENY_ALL).start(request).firstStep();

        // THEN the generic bearer-token handling fails the same way as OAUTHBEARER
        assertThat(step).isEqualTo(new SaslStep.Failure("OAuth authentication failed.",
            SaslStep.Failure.Cause.AUTHENTICATION_FAILED, Optional.empty(), Optional.of(USER)));
    }

    @Test
    void shouldChallengeWhenNoInitialResponse() {
        // GIVEN an OIDC SASL exchange without SASL-IR
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanism.NAME, Optional.empty());

        // WHEN the mechanism starts
        SaslStep firstStep = new OauthBearerSaslMechanism(Optional.empty(), DENY_ALL).start(request).firstStep();

        // THEN the server asks for one client response
        assertThat(firstStep).isEqualTo(new SaslStep.Challenge(Optional.empty()));
    }

    @Test
    void shouldFailMalformedResponse() {
        // GIVEN a malformed OIDC SASL response
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanism.NAME,
            Optional.of(bytes("invalid")));

        // WHEN the mechanism consumes the response
        SaslStep step = new OauthBearerSaslMechanism(Optional.empty(), DENY_ALL).start(request).firstStep();

        // THEN it fails before any token validation
        assertThat(step).isEqualTo(new SaslStep.Failure("Malformed authentication command.",
            SaslStep.Failure.Cause.MALFORMED_COMMAND, Optional.empty(), Optional.empty()));
    }

    @Test
    void parseConfigurationShouldReturnEmptyWhenNoOidcDeclaration() throws Exception {
        // GIVEN a server configuration without an auth.oidc block
        BaseHierarchicalConfiguration serverConfiguration = new BaseHierarchicalConfiguration();

        // WHEN the OIDC declaration is parsed
        // THEN no OIDC configuration is available to the mechanism
        assertThat(OidcSaslMechanisms.parseConfiguration(serverConfiguration)).isEmpty();
    }

    @Test
    void parseConfigurationShouldReturnConfigurationWhenOidcDeclared() throws Exception {
        // GIVEN a server configuration with an auth.oidc block
        BaseHierarchicalConfiguration serverConfiguration = new BaseHierarchicalConfiguration();
        serverConfiguration.addProperty("auth.oidc.jwksURL", "https://auth.example.com/jwks");
        serverConfiguration.addProperty("auth.oidc.claim", "email");
        serverConfiguration.addProperty("auth.oidc.oidcConfigurationURL", "https://auth.example.com/.well-known/openid-configuration");
        serverConfiguration.addProperty("auth.oidc.scope", "openid");
        serverConfiguration.addProperty("auth.oidc.aud", "james");

        // WHEN the OIDC declaration is parsed
        // THEN the per-server OIDC configuration is available to the mechanism
        assertThat(OidcSaslMechanisms.parseConfiguration(serverConfiguration))
            .hasValueSatisfying(configuration -> assertThat(configuration.getClaim()).isEqualTo("email"));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
