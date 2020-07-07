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

package org.apache.james.modules.mailbox;

import java.io.FileNotFoundException;
import java.util.Set;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.KeyspaceFactory;
import org.apache.james.backends.cassandra.init.ResilientClusterProvider;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.backends.cassandra.init.configuration.InjectionNames;
import org.apache.james.backends.cassandra.init.configuration.KeyspaceConfiguration;
import org.apache.james.util.Host;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.Session;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class CassandraCacheSessionModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraSessionModule.class);

    private static final String LOCALHOST = "127.0.0.1";
    private static final String CASSANDRA_FILE_NAME = "cassandra";
    private static final int CASSANDRA_PORT = 9042;

    @Override
    protected void configure() {
        bind(InitializedCacheCluster.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder(), CassandraModule.class, Names.named(InjectionNames.CACHE));
    }

    @Named(InjectionNames.CACHE)
    @Provides
    @Singleton
    KeyspaceConfiguration provideCacheKeyspaceConfiguration(KeyspacesConfiguration keyspacesConfiguration) {
        return keyspacesConfiguration.cacheKeyspaceConfiguration();
    }

    @Singleton
    @Named(InjectionNames.CACHE)
    @Provides
    Session provideSession(@Named(InjectionNames.CACHE) KeyspaceConfiguration keyspaceConfiguration,
                           InitializedCacheCluster cluster,
                           @Named(InjectionNames.CACHE) CassandraModule module) {
        return new SessionWithInitializedTablesFactory(keyspaceConfiguration, cluster.cluster, module).get();
    }

    @Singleton
    @Named(InjectionNames.CACHE)
    @Provides
    Cluster provideCluster(@Named(InjectionNames.CACHE) ClusterConfiguration clusterConfiguration) {
        PoolingOptions cachingPoolingOptions = clusterConfiguration.getPoolingOptions()
            .orElse(new PoolingOptions());
        cachingPoolingOptions.setMaxQueueSize(0);

        return new ResilientClusterProvider(ClusterConfiguration.Builder.from(clusterConfiguration)
            .poolingOptions(cachingPoolingOptions)
            .build())
            .get();
    }

    @Provides
    @Named(InjectionNames.CACHE)
    @Singleton
    ClusterConfiguration provideClusterConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return ClusterConfiguration.from(propertiesProvider.getConfiguration(CASSANDRA_FILE_NAME));
        } catch (FileNotFoundException e) {
            return ClusterConfiguration.builder()
                .host(Host.from(LOCALHOST, CASSANDRA_PORT))
                .build();
        }
    }

    @Named(InjectionNames.CACHE)
    @Provides
    @Singleton
    CassandraModule composeCacheDefinitions(@Named(InjectionNames.CACHE) Set<CassandraModule> modules) {
        return CassandraModule.aggregateModules(modules);
    }

    static class InitializedCacheCluster {
        private final Cluster cluster;

        @Inject
        private InitializedCacheCluster(@Named(InjectionNames.CACHE) Cluster cluster, ClusterConfiguration clusterConfiguration, KeyspacesConfiguration keyspacesConfiguration) {
            this.cluster = cluster;

            if (clusterConfiguration.shouldCreateKeyspace()) {
                KeyspaceFactory.createKeyspace(keyspacesConfiguration.cacheKeyspaceConfiguration(), cluster);
            }
        }
    }
}
