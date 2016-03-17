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

package org.apache.james.jmap;

import static org.apache.james.jmap.BypassAuthOnRequestMethod.bypass;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.http.jetty.Configuration;
import org.apache.james.http.jetty.Configuration.Builder;
import org.apache.james.http.jetty.JettyHttpServer;
import org.apache.james.lifecycle.api.Configurable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

@Singleton
public class JMAPServer implements Configurable {

    private static final Logger TIMELINE_LOGGER = LoggerFactory.getLogger("timeline");

    private final JettyHttpServer server;

    @Inject
    private JMAPServer(JMAPConfiguration jmapConfiguration,
                       AuthenticationServlet authenticationServlet, JMAPServlet jmapServlet,
                       AuthenticationFilter authenticationFilter, FirstUserConnectionFilter firstUserConnectionFilter) {
        TIMELINE_LOGGER.info("9 JMAP_Server servlet creation started");
        server = JettyHttpServer.create(
                configurationBuilderFor(jmapConfiguration)
                        .serve("/authentication")
                            .with(authenticationServlet)
                        .filter("/authentication")
                            .with(new AllowAllCrossOriginRequests(bypass(authenticationFilter).on("POST").and("OPTIONS").only()))
                            .only()
                        .serve("/jmap")
                            .with(jmapServlet)
                        .filter("/jmap")
                            .with(new AllowAllCrossOriginRequests(bypass(authenticationFilter).on("OPTIONS").only()))
                            .and(firstUserConnectionFilter)
                            .only()
                        .build());
        TIMELINE_LOGGER.info("9 JMAP_Server servlet creation done");
    }

    private Builder configurationBuilderFor(JMAPConfiguration jmapConfiguration) {
        Builder builder = Configuration.builder();
        if (jmapConfiguration.getPort().isPresent()) {
            builder.port(jmapConfiguration.getPort().get());
        } else {
            builder.randomPort();
        }
        return builder;
    }

    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        try {
            TIMELINE_LOGGER.info("10 JMAP_Server startup started");
            server.start();
            TIMELINE_LOGGER.info("10 JMAP_Server startup done");
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    @PreDestroy
    public void stop() {
        TIMELINE_LOGGER.info("11 JMAP_Server stop started");
        try {
            server.stop();
        } catch (Exception e) {
            Throwables.propagate(e);
        }
        TIMELINE_LOGGER.info("11 JMAP_Server stop done");
    }

    public int getPort() {
        return server.getPort();
    }
}
