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

package org.apache.james.routes;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;

import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TransferEmailRoutes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public class TransferEmailRoutesTest {

    private WebAdminServer webAdminServer;

    private WebAdminServer createServer() {
        MemoryMailQueueFactory memoryMailQueueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        return WebAdminUtils.createWebAdminServer(new TransferEmailRoutes(memoryMailQueueFactory)).start();
    }

    @BeforeEach
    void setup() {
        webAdminServer = createServer();
        RestAssured.requestSpecification = buildRequestSpecification(webAdminServer);
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    RequestSpecification buildRequestSpecification(WebAdminServer server) {
        return new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setBasePath(TransferEmailRoutes.BASE_URL)
                .setPort(server.getPort().getValue())
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .build();
    }


    @Test
    public void whenToIsMissingInRequestThenRequestFails() {
        given()
                .body(ClassLoaderUtils.getSystemResourceAsString("message/rfc822/message-without-tos.eml"))
                .when()
                .post()
                .then()
                .assertThat()
                .statusCode(500);
    }

    @Test
    public void statusCode201ReturnedWhenSendingMailWithAllRequiredFields() {
        given()
                .body(ClassLoaderUtils.getSystemResourceAsString("message/rfc822/message.eml"))
                .when()
                .post()
                .then()
                .assertThat()
                .statusCode(201);
    }

    @Test
    public void requestFailsOnEmptyBody() {
        given()
                .body("")
                .when()
                .post()
                .then()
                .assertThat()
                .statusCode(500);
    }
}
