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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.jwt.OidcSASLConfiguration;
import org.apache.james.mailbox.Authorizator;
import org.junit.jupiter.api.Test;

class OidcSaslMechanismTest {
    private static final Authorizator DENY_ALL = (userId, otherUserId) -> Authorizator.AuthorizationState.FORBIDDEN;

    private static HierarchicalConfiguration<ImmutableNode> oidcServerConfiguration() {
        BaseHierarchicalConfiguration serverConfiguration = new BaseHierarchicalConfiguration();
        serverConfiguration.addProperty("auth.oidc.jwksURL", "https://auth.example.com/jwks");
        serverConfiguration.addProperty("auth.oidc.claim", "email");
        serverConfiguration.addProperty("auth.oidc.oidcConfigurationURL", "https://auth.example.com/.well-known/openid-configuration");
        serverConfiguration.addProperty("auth.oidc.scope", "openid");
        serverConfiguration.addProperty("auth.oidc.aud", "james");
        return serverConfiguration;
    }

    private static OidcSASLConfiguration oidcConfiguration() throws ConfigurationException {
        return OAuthSaslMechanism.parseConfiguration(oidcServerConfiguration());
    }

    @Test
    void shouldChallengeWhenNoInitialResponse() throws Exception {
        // GIVEN an OIDC SASL exchange without SASL-IR
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanismFactory.NAME, Optional.empty());

        // WHEN the mechanism starts
        SaslStep firstStep = new OAuthSaslMechanism(OauthBearerSaslMechanismFactory.NAME, oidcConfiguration(), DENY_ALL).start(request).firstStep();

        // THEN the server asks for one client response
        assertThat(firstStep).isEqualTo(new SaslStep.Challenge(Optional.empty()));
    }

    @Test
    void shouldFailMalformedResponse() throws Exception {
        // GIVEN a malformed OIDC SASL response
        SaslInitialRequest request = new SaslInitialRequest(XOauth2SaslMechanismFactory.NAME,
            Optional.of(bytes("invalid")));

        // WHEN the mechanism consumes the response
        SaslStep step = new OAuthSaslMechanism(XOauth2SaslMechanismFactory.NAME, oidcConfiguration(), DENY_ALL).start(request).firstStep();

        // THEN it fails before any token validation
        assertThat(step).isEqualTo(new SaslStep.Failure("Malformed authentication command.",
            SaslStep.Failure.Cause.MALFORMED_COMMAND, Optional.empty(), Optional.empty()));
    }

    @Test
    void hasConfigurationShouldBeFalseWhenNoOidcDeclaration() {
        // GIVEN a server configuration without an auth.oidc block
        // THEN OIDC is detected as not configured
        assertThat(OAuthSaslMechanism.hasConfiguration(new BaseHierarchicalConfiguration())).isFalse();
    }

    @Test
    void parseConfigurationShouldThrowWhenNoOidcDeclaration() {
        // GIVEN a server configuration without an auth.oidc block
        // WHEN the OIDC declaration is parsed
        // THEN it fails fast rather than degrading to an always-failing OAuth mechanism
        assertThatThrownBy(() -> OAuthSaslMechanism.parseConfiguration(new BaseHierarchicalConfiguration()))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void parseConfigurationShouldReturnConfigurationWhenOidcDeclared() throws Exception {
        // GIVEN a server configuration with an auth.oidc block
        // WHEN the OIDC declaration is parsed
        // THEN the per-server OIDC configuration is available to the mechanism
        assertThat(OAuthSaslMechanism.parseConfiguration(oidcServerConfiguration()).getClaim()).isEqualTo("email");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
