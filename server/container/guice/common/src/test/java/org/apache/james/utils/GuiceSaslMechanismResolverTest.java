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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.api.sasl.TestingDefaultPackageSaslMechanismFactory;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class GuiceSaslMechanismResolverTest {
    private static final HierarchicalConfiguration<ImmutableNode> EMPTY_CONFIGURATION = new BaseHierarchicalConfiguration();

    private final GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new MapBackedSaslMechanismInstantiator());

    @Test
    void resolveShouldResolveSimpleNameFromDefaultSaslPackage() throws Exception {
        // GIVEN a resolver using a test instantiator that models James default SASL package resolution
        // WHEN resolving a simple factory class name
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of("TestingDefaultPackageSaslMechanismFactory"),
            EMPTY_CONFIGURATION);

        // THEN the factory is instantiated from org.apache.james.protocols.api.sasl and creates the mechanism
        assertThat(mechanisms).hasOnlyElementsOfType(TestingDefaultPackageSaslMechanismFactory.TestingDefaultPackageSaslMechanism.class);
    }

    @Test
    void resolveShouldResolveFullyQualifiedClassName() throws Exception {
        // GIVEN a resolver that also accepts extension factory class names
        // WHEN resolving a fully qualified factory class name
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(ExternalFakeSaslMechanismFactory.class.getCanonicalName()),
            EMPTY_CONFIGURATION);

        // THEN the mechanism is created without relying on the default package
        assertThat(mechanisms).hasOnlyElementsOfType(ExternalFakeSaslMechanismFactory.ExternalFakeSaslMechanism.class);
    }

    @Test
    void resolveShouldCreateMechanismsFromCurrentServerConfiguration() throws Exception {
        // GIVEN two server configurations using the same configured SASL mechanism factory
        BaseHierarchicalConfiguration firstConfiguration = new BaseHierarchicalConfiguration();
        firstConfiguration.addProperty("auth.example.realm", "first.example.org");
        BaseHierarchicalConfiguration secondConfiguration = new BaseHierarchicalConfiguration();
        secondConfiguration.addProperty("auth.example.realm", "second.example.org");

        // WHEN resolving the same configured factory for each server
        SaslMechanism firstMechanism = testee.resolve(ImmutableList.of(ConfiguredSaslMechanismFactory.class.getCanonicalName()),
            firstConfiguration)
            .getFirst();
        SaslMechanism secondMechanism = testee.resolve(ImmutableList.of(ConfiguredSaslMechanismFactory.class.getCanonicalName()),
            secondConfiguration)
            .getFirst();

        // THEN each mechanism is created from that server's configuration, not from a global singleton
        assertThat(firstMechanism)
            .isInstanceOfSatisfying(ConfiguredSaslMechanism.class,
                mechanism -> assertThat(mechanism.realm()).isEqualTo("first.example.org"));
        assertThat(secondMechanism)
            .isInstanceOfSatisfying(ConfiguredSaslMechanism.class,
                mechanism -> assertThat(mechanism.realm()).isEqualTo("second.example.org"));
    }

    @Test
    void resolveShouldPreserveConfiguredOrderForDistinctMechanisms() throws Exception {
        // GIVEN a configured factory list with distinct SASL mechanism names
        // WHEN resolving the list
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(
                ExternalFakeSaslMechanismFactory.class.getCanonicalName(),
                "TestingDefaultPackageSaslMechanismFactory"),
            EMPTY_CONFIGURATION);

        // THEN configured order is preserved
        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly("EXTERNAL-FAKE", "DEFAULT");
    }

    @Test
    void resolveShouldDeduplicateMechanismNamesCaseInsensitively() throws Exception {
        // GIVEN two configured factories creating the same SASL mechanism name with different case
        // WHEN resolving both factories
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(
                DuplicateUpperCaseSaslMechanismFactory.class.getCanonicalName(),
                DuplicateLowerCaseSaslMechanismFactory.class.getCanonicalName()),
            EMPTY_CONFIGURATION);

        // THEN first occurrence wins and configured order remains stable
        assertThat(mechanisms)
            .hasSize(1)
            .extracting(SaslMechanism::name)
            .containsExactly("DUPLICATE");
    }

    @Test
    void resolveShouldFailWhenClassDoesNotExist() {
        // GIVEN a resolver used for configured SASL mechanism factory entries
        // WHEN resolving an unknown class name
        // THEN startup wiring can fail fast with the configured entry in the error
        assertThatThrownBy(() -> testee.resolve(ImmutableList.of("MissingSaslMechanismFactory"), EMPTY_CONFIGURATION))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("MissingSaslMechanismFactory");
    }

    @Test
    void resolveShouldPropagateFactoryConfigurationFailure() {
        // GIVEN a factory rejecting its server configuration
        // WHEN resolving it against a configuration missing the expected property
        // THEN the factory failure surfaces as a configuration error
        assertThatThrownBy(() -> testee.resolve(ImmutableList.of(ConfiguredSaslMechanismFactory.class.getCanonicalName()),
                EMPTY_CONFIGURATION))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("auth.example.realm");
    }

    private static class MapBackedSaslMechanismInstantiator implements SaslMechanismInstantiator {
        private final Map<String, Class<? extends SaslMechanismFactory>> classes = ImmutableMap.<String, Class<? extends SaslMechanismFactory>>builder()
            .put("TestingDefaultPackageSaslMechanismFactory", TestingDefaultPackageSaslMechanismFactory.class)
            .put(TestingDefaultPackageSaslMechanismFactory.class.getCanonicalName(), TestingDefaultPackageSaslMechanismFactory.class)
            .put(ExternalFakeSaslMechanismFactory.class.getCanonicalName(), ExternalFakeSaslMechanismFactory.class)
            .put(ConfiguredSaslMechanismFactory.class.getCanonicalName(), ConfiguredSaslMechanismFactory.class)
            .put(DuplicateUpperCaseSaslMechanismFactory.class.getCanonicalName(), DuplicateUpperCaseSaslMechanismFactory.class)
            .put(DuplicateLowerCaseSaslMechanismFactory.class.getCanonicalName(), DuplicateLowerCaseSaslMechanismFactory.class)
            .build();

        @Override
        public SaslMechanismFactory instantiate(ClassName className) throws ClassNotFoundException {
            try {
                return Optional.ofNullable(classes.get(className.getName()))
                    .orElseThrow(() -> new ClassNotFoundException(className.getName()))
                    .getDeclaredConstructor()
                    .newInstance();
            } catch (ReflectiveOperationException e) {
                throw new ClassNotFoundException(className.getName(), e);
            }
        }
    }

    public static class ConfiguredSaslMechanismFactory implements SaslMechanismFactory {
        @Override
        public SaslMechanism create(HierarchicalConfiguration<ImmutableNode> serverConfiguration) throws ConfigurationException {
            return Optional.ofNullable(serverConfiguration.getString("auth.example.realm", null))
                .map(ConfiguredSaslMechanism::new)
                .orElseThrow(() -> new ConfigurationException("auth.example.realm must be configured"));
        }
    }

    private static class ConfiguredSaslMechanism extends FixedNameSaslMechanism {
        private final String realm;

        private ConfiguredSaslMechanism(String realm) {
            super("CONFIGURED");
            this.realm = realm;
        }

        private String realm() {
            return realm;
        }
    }

    public static class DuplicateUpperCaseSaslMechanismFactory implements SaslMechanismFactory {
        @Override
        public SaslMechanism create(HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
            return new FixedNameSaslMechanism("DUPLICATE");
        }
    }

    public static class DuplicateLowerCaseSaslMechanismFactory implements SaslMechanismFactory {
        @Override
        public SaslMechanism create(HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
            return new FixedNameSaslMechanism("duplicate");
        }
    }

    private static class FixedNameSaslMechanism implements SaslMechanism {
        private final String name;

        private FixedNameSaslMechanism(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public SaslExchange start(SaslInitialRequest request) {
            return new FixedStepExchange();
        }
    }

    private record FixedStepExchange() implements SaslExchange {
        @Override
        public SaslStep firstStep() {
            return new SaslStep.Failure("not implemented");
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return new SaslStep.Failure("not implemented");
        }

        @Override
        public void abort() {
        }

        @Override
        public void close() {
        }
    }
}
