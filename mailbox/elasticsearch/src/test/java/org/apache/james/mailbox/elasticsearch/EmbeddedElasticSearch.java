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

package org.apache.james.mailbox.elasticsearch;

import static com.jayway.awaitility.Awaitility.await;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.io.IOException;

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.flush.FlushRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.node.Node;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.awaitility.Duration;

public class EmbeddedElasticSearch extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedElasticSearch.class);
    private static final Logger TIMELINE_LOGGER = LoggerFactory.getLogger("timeline");


    private Node node;
    
    @Override
    public void before() throws IOException {
        TIMELINE_LOGGER.info("6 ElasticSearch starting embedded server");
        node = nodeBuilder().local(true)
            .settings(ImmutableSettings.builder()
                .put("index.store.type", "memory")
                .put("index.store.fs.memory.enabled", "true")
                .put("script.disable_dynamic",true)
                .build())
            .node();
        node.start();
        awaitForElasticSearch();
        TIMELINE_LOGGER.info("6 ElasticSearch starting embedded server done");
    }

    @Override
    public void after() {
        TIMELINE_LOGGER.info("5 ElasticSearch stopping embedded server");
        awaitForElasticSearch();
        try (Client client = node.client()) {
            client.admin()
                .indices()
                .delete(new DeleteIndexRequest(ElasticSearchIndexer.MAILBOX_INDEX))
                .actionGet();
        } catch (Exception e) {
            LOGGER.warn("Error while closing ES connection", e);
        }
        node.close();
        TIMELINE_LOGGER.info("5 ElasticSearch stopping embedded server done");
    }

    public Node getNode() {
        return node;
    }

    /**
     * Sometimes, tests are too fast.
     * This method ensure that ElasticSearch service is up and indices are updated
     */
    public void awaitForElasticSearch() {
        await().atMost(Duration.TEN_SECONDS).until(this::flush);
    }

    private boolean flush() {
        try (Client client = node.client()) {
            new FlushRequestBuilder(client.admin().indices()).setForce(true).get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
