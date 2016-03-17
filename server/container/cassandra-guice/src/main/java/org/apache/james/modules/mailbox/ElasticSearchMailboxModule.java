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

import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.elasticsearch.ClientProvider;
import org.apache.james.mailbox.elasticsearch.ClientProviderImpl;
import org.apache.james.mailbox.elasticsearch.IndexCreationFactory;
import org.apache.james.mailbox.elasticsearch.NodeMappingFactory;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.store.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.tika.extractor.TikaTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;

public class ElasticSearchMailboxModule extends AbstractModule {

    private static final Logger TIMELINE_LOGGER = LoggerFactory.getLogger("timeline");

    @Override
    protected void configure() {
        bind(new TypeLiteral<MessageSearchIndex<CassandraId>>(){}).to(new TypeLiteral<ElasticSearchListeningMessageSearchIndex<CassandraId>>() {});
        bind(TextExtractor.class).to(TikaTextExtractor.class);
    }

    @Provides
    @Singleton
    protected ClientProvider provideClientProvider(FileSystem fileSystem) throws ConfigurationException, FileNotFoundException {
        PropertiesConfiguration propertiesReader = new PropertiesConfiguration(fileSystem.getFile(FileSystem.FILE_PROTOCOL_AND_CONF + "elasticsearch.properties"));
        ClientProvider clientProvider = createClientProvider(propertiesReader);
        createIndex(propertiesReader, clientProvider);
        applyMapping(clientProvider);
        return clientProvider;
    }

    private ClientProvider createClientProvider(PropertiesConfiguration propertiesReader) {
        TIMELINE_LOGGER.info("15 ElasticSearch ClientProvider instantiation started");
        ClientProvider clientProvider = new ClientProviderImpl(propertiesReader.getString("elasticsearch.masterHost"),
            propertiesReader.getInt("elasticsearch.port"));
        TIMELINE_LOGGER.info("15 ElasticSearch ClientProvider instantiation done");
        return clientProvider;
    }

    private void createIndex(PropertiesConfiguration propertiesReader, ClientProvider clientProvider) {
        TIMELINE_LOGGER.info("16 ElasticSearch index creation started");
        IndexCreationFactory.createIndex(clientProvider,
            propertiesReader.getInt("elasticsearch.nb.shards"),
            propertiesReader.getInt("elasticsearch.nb.replica"));
        TIMELINE_LOGGER.info("16 ElasticSearch index creation done");
    }

    private void applyMapping(ClientProvider clientProvider) {
        TIMELINE_LOGGER.info("17 ElasticSearch ClientProvider mapping started");
        NodeMappingFactory.applyMapping(clientProvider);
        TIMELINE_LOGGER.info("17 ElasticSearch ClientProvider mapping done");
    }

}
