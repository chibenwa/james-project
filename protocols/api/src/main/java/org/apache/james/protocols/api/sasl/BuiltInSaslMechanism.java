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

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;

/**
 * The SASL mechanisms James offers when a server does not list its mechanisms explicitly, together with the
 * legacy gating deciding whether each one is enabled for a given server configuration: PLAIN unless disabled
 * through {@code auth.plainAuthEnabled}, and the OAuth mechanisms only when an {@code auth.oidc} section exists.
 *
 * Single source of truth shared by the Guice wiring (which needs factory class names) and the non-Guice wiring
 * (which builds factory instances directly).
 */
public enum BuiltInSaslMechanism {
    PLAIN(PlainSaslMechanismFactory.class,
        (authenticator, authorizator) -> new PlainSaslMechanismFactory(authenticator, authorizator),
        configuration -> configuration.getBoolean("auth.plainAuthEnabled", true)),
    OAUTHBEARER(OauthBearerSaslMechanismFactory.class,
        (authenticator, authorizator) -> new OauthBearerSaslMechanismFactory(authorizator),
        OidcSaslMechanisms::hasConfiguration),
    XOAUTH2(XOauth2SaslMechanismFactory.class,
        (authenticator, authorizator) -> new XOauth2SaslMechanismFactory(authorizator),
        OidcSaslMechanisms::hasConfiguration);

    private final Class<? extends SaslMechanismFactory> factoryClass;
    private final BiFunction<Authenticator, Authorizator, SaslMechanismFactory> factory;
    private final Predicate<HierarchicalConfiguration<ImmutableNode>> enabled;

    BuiltInSaslMechanism(Class<? extends SaslMechanismFactory> factoryClass,
                         BiFunction<Authenticator, Authorizator, SaslMechanismFactory> factory,
                         Predicate<HierarchicalConfiguration<ImmutableNode>> enabled) {
        this.factoryClass = factoryClass;
        this.factory = factory;
        this.enabled = enabled;
    }

    public static Stream<BuiltInSaslMechanism> enabledFor(HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
        return Arrays.stream(values()).filter(mechanism -> mechanism.enabled.test(serverConfiguration));
    }

    public Class<? extends SaslMechanismFactory> factoryClass() {
        return factoryClass;
    }

    public SaslMechanismFactory factory(Authenticator authenticator, Authorizator authorizator) {
        return factory.apply(authenticator, authorizator);
    }
}
