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

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.jwt.OidcSASLConfiguration;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.protocols.api.OIDCSASLParser;

public final class OidcSaslMechanisms {
    private static final String OIDC_PATH = "auth.oidc";

    static SaslExchange start(Optional<byte[]> initialResponse, OidcSASLConfiguration oidcConfiguration, Authorizator authorizator) {
        return new OidcSaslExchange(initialResponse, oidcConfiguration, authorizator);
    }

    /**
     * Whether the server configuration block declares an OIDC section.
     */
    public static boolean hasConfiguration(HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
        return !serverConfiguration.immutableConfigurationsAt(OIDC_PATH).isEmpty();
    }

    /**
     * Parses the OIDC declaration of one server configuration block.
     *
     * @throws ConfigurationException when the OIDC section is missing or malformed: OIDC mechanisms must not
     * silently degrade to an always-failing exchange.
     */
    public static OidcSASLConfiguration parseConfiguration(HierarchicalConfiguration<ImmutableNode> serverConfiguration) throws ConfigurationException {
        if (!hasConfiguration(serverConfiguration)) {
            throw new ConfigurationException("OAuth SASL mechanisms require an 'auth.oidc' configuration");
        }
        try {
            return OidcSASLConfiguration.parse(serverConfiguration.configurationAt(OIDC_PATH));
        } catch (MalformedURLException | URISyntaxException e) {
            throw new ConfigurationException("Invalid OIDC SASL configuration", e);
        }
    }

    private OidcSaslMechanisms() {
    }

    private static class OidcSaslExchange implements SaslExchange {
        private final Optional<byte[]> initialResponse;
        private final OidcSASLConfiguration oidcConfiguration;
        private final Authorizator authorizator;

        private OidcSaslExchange(Optional<byte[]> initialResponse, OidcSASLConfiguration oidcConfiguration, Authorizator authorizator) {
            this.initialResponse = initialResponse;
            this.oidcConfiguration = oidcConfiguration;
            this.authorizator = authorizator;
        }

        @Override
        public SaslStep firstStep() {
            return initialResponse
                .map(this::authenticate)
                .orElseGet(() -> new SaslStep.Challenge(Optional.empty()));
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return authenticate(clientResponse);
        }

        @Override
        public void abort() {
        }

        @Override
        public void close() {
        }

        private SaslStep authenticate(byte[] clientResponse) {
            return OIDCSASLParser.parseDecoded(new String(clientResponse, StandardCharsets.US_ASCII))
                .map(response -> verify(response.getToken(), Username.of(response.getAssociatedUser())))
                .orElseGet(() -> new SaslStep.Failure("Malformed authentication command.",
                    SaslStep.Failure.Cause.MALFORMED_COMMAND, Optional.empty(), Optional.empty()));
        }

        private SaslStep verify(String token, Username authorizationId) {
            return new OidcJwtTokenVerifier(oidcConfiguration).validateToken(token)
                .map(authenticatedUser -> SaslDelegation.authorize(authorizator, authenticatedUser, authorizationId))
                .orElseGet(() -> new SaslStep.Failure("OAuth authentication failed.",
                    SaslStep.Failure.Cause.AUTHENTICATION_FAILED, Optional.empty(), Optional.of(authorizationId)));
        }
    }
}
