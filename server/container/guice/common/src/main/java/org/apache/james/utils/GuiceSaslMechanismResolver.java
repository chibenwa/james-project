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

package org.apache.james.utils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

/**
 * Creates the SASL mechanisms of one server from its configured {@link SaslMechanismFactory} class names.
 */
public class GuiceSaslMechanismResolver {
    private final SaslMechanismInstantiator instantiator;

    @Inject
    public GuiceSaslMechanismResolver(SaslMechanismInstantiator instantiator) {
        this.instantiator = instantiator;
    }

    public ImmutableList<SaslMechanism> resolve(Collection<String> factoryClassNames,
                                                HierarchicalConfiguration<ImmutableNode> serverConfiguration) throws ConfigurationException {
        try {
            return factoryClassNames.stream()
                .map(ClassName::new)
                .map(Throwing.function(className -> create(className, serverConfiguration)))
                .collect(Collectors.toMap(
                    mechanism -> normalize(mechanism.name()),
                    Function.identity(),
                    (first, second) -> first,
                    LinkedHashMap::new))
                .values()
                .stream()
                .collect(ImmutableList.toImmutableList());
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ConfigurationException configurationException) {
                throw configurationException;
            }
            throw e;
        }
    }

    private SaslMechanism create(ClassName factoryClassName,
                                 HierarchicalConfiguration<ImmutableNode> serverConfiguration) throws ConfigurationException {
        return instantiate(factoryClassName).create(serverConfiguration);
    }

    private SaslMechanismFactory instantiate(ClassName factoryClassName) throws ConfigurationException {
        try {
            return instantiator.instantiate(factoryClassName);
        } catch (Exception e) {
            throw new ConfigurationException("Can not load SASL mechanism factory " + factoryClassName.getName(), e);
        }
    }

    private String normalize(String mechanismName) {
        return mechanismName.toUpperCase(Locale.US);
    }
}
