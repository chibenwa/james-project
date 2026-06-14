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

package org.apache.james.modules.protocols;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.BuiltInSaslMechanism;

import com.google.common.collect.ImmutableList;

public class JamesDefaultImapSaslMechanismClassNamesProvider implements DefaultImapSaslMechanismClassNamesProvider {
    /**
     * Default mechanism list used when "auth.saslMechanisms" is not configured. The enablement rules live in
     * {@link BuiltInSaslMechanism}; the resolver re-instantiates these factory classes through Guice.
     */
    @Override
    public ImmutableList<String> resolve(HierarchicalConfiguration<ImmutableNode> configuration) {
        return BuiltInSaslMechanism.enabledFor(configuration)
            .map(mechanism -> mechanism.factoryClass().getSimpleName())
            .collect(ImmutableList.toImmutableList());
    }
}
