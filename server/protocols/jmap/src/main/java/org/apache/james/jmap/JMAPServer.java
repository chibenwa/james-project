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

import java.util.Optional;

import org.apache.james.util.Port;

import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

public class JMAPServer {
    private static final int RANDOM_PORT = 0;

    private final JMAPConfiguration configuration;
    private Optional<DisposableServer> server;

    public JMAPServer(JMAPConfiguration configuration) {
        this.configuration = configuration;
        this.server = Optional.empty();
    }

    public Port getPort() {
        return server.map(DisposableServer::port)
            .map(Port::of)
            .orElseThrow(() -> new IllegalStateException("port is not available because server is not started or disabled"));
    }

    public void start() {
        if (configuration.isEnabled()) {
            server = Optional.of(HttpServer.create()
                .port(configuration.getPort()
                    .map(Port::getValue)
                    .orElse(RANDOM_PORT))
                .handle((request, response) -> request.receive().then(response.sendNotFound()))
                .bindNow());
        }
    }

    public void stop() {
        server.ifPresent(DisposableServer::disposeNow);
    }
}
