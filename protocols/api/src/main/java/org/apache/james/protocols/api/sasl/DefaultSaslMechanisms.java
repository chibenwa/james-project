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

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;

import com.google.common.collect.ImmutableList;

/**
 * Default SASL mechanisms used when a server does not explicitly list its mechanisms.
 *
 * This is where the legacy "auth.plainAuthEnabled" flag and the OIDC-presence gating ("supportsOAuth")
 * are honoured for backward compatibility: PLAIN is offered unless explicitly disabled, and the OAuth
 * mechanisms are offered only when an OIDC section is configured. Servers that list mechanisms explicitly
 * bypass this and own the decision (and a configured OAuth mechanism without OIDC fails fast).
 *
 * The {@link Authenticator}/{@link Authorizator} binding stays in the SASL layer (through the factories),
 * so protocol stacks only ever receive immutable {@link SaslMechanism} instances.
 */
public final class DefaultSaslMechanisms {
    public static ImmutableList<SaslMechanism> create(Authenticator authenticator, Authorizator authorizator,
                                                      HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
        try {
            ImmutableList.Builder<SaslMechanism> mechanisms = ImmutableList.builder();
            for (BuiltInSaslMechanism mechanism : BuiltInSaslMechanism.enabledFor(serverConfiguration).toList()) {
                mechanisms.add(mechanism.factory(authenticator, authorizator).create(serverConfiguration));
            }
            return mechanisms.build();
        } catch (ConfigurationException e) {
            throw new RuntimeException("Failed to build default SASL mechanisms", e);
        }
    }

    public static ImmutableList<SaslMechanism> create(Authenticator authenticator, Authorizator authorizator) {
        return create(authenticator, authorizator, new BaseHierarchicalConfiguration());
    }

    private DefaultSaslMechanisms() {
    }
}
