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

package org.apache.james.jwt.introspection;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.lambdas.Throwing;

import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.resources.ConnectionProvider;

public class DefaultIntrospectionClient implements IntrospectionClient {

    public static final String TOKEN_ATTRIBUTE = "token";
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIntrospectionClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper deserializer;

    public DefaultIntrospectionClient() {
        this.httpClient = HttpClient.create(ConnectionProvider.builder(this.getClass().getName())
                .build())
            .disableRetry(true)
            .headers(builder -> {
                builder.add("Accept", "application/json");
                builder.add("Content-Type", "application/x-www-form-urlencoded");
            });
        this.deserializer = new ObjectMapper();
    }

    @Override
    public Publisher<TokenIntrospectionResponse> introspect(IntrospectionEndpoint introspectionEndpoint, String token) {
        return httpClient
            .headers(builder -> introspectionEndpoint.getAuthorizationHeader()
                .ifPresent(auth -> builder.add("Authorization", auth)))
            .post()
            .uri(introspectionEndpoint.getUrl().toString())
            .sendForm((req, form) -> form.multipart(false)
                .attr(TOKEN_ATTRIBUTE, token))
            .responseSingle(this::afterHTTPResponseHandler);
    }

    private Mono<TokenIntrospectionResponse> afterHTTPResponseHandler(HttpClientResponse httpClientResponse, ByteBufMono dataBuf) {
        return Mono.just(httpClientResponse.status())
            .filter(httpStatus -> httpStatus.equals(HttpResponseStatus.OK))
            .flatMap(httpStatus -> dataBuf.asByteArray())
            .map(Throwing.function(deserializer::readTree))
            .map(TokenIntrospectionResponse::parse)
            .onErrorResume(error -> Mono.error(new TokenIntrospectionException("Error when introspecting token.", error)))
            .switchIfEmpty(dataBuf.asString(StandardCharsets.UTF_8)
                .switchIfEmpty(Mono.just(""))
                .flatMap(errorResponse -> Mono.error(new TokenIntrospectionException(
                    String.format("Error when introspecting token. \nResponse Status = %s,\n Response Body = %s",
                        httpClientResponse.status().code(), errorResponse)))));
    }

    @Override
    public Publisher<Void> userInfo(URI url, String token) {
        return httpClient
            .headers(builder -> builder.add("Authorization", "Bearer " + token))
            .get()
            .uri(url)
            .responseSingle((headers, body) -> {
                if (headers.status().code() != 200) {
                    LOGGER.info("UserInfo lookup on {} for {} failed with code {}", url, token, headers.status().code());
                    return Mono.error(new RuntimeException("UserInfo lookup failed with code " + headers.status().code()));
                }
                return body.asByteArray()
                    .map(Throwing.function(deserializer::readTree))
                    .handle((json, sink) -> {
                        JsonNode error = json.get("error");
                        if (error instanceof TextNode) {
                            TextNode textNode = (TextNode) error;
                            LOGGER.info("UserInfo lookup on {} for {} failed with error {}", url, token, textNode.asText());
                            sink.error(new RuntimeException("UserInfo lookup failed with error " + textNode.asText()));
                        }
                        sink.complete();
                    })
                    .then();
            })
            .then();
    }
}
