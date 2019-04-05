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
package org.apache.james.linshare;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.DockerComposeContainer;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

@ExtendWith(LinshareExtension.class)
class LinshareTest {
    @BeforeEach
    void setup(DockerComposeContainer<?> linshare) {
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(linshare.getServicePort(LinshareExtension.LINSHARE_BACKEND_SERVICE, LinshareExtension.LINSHARE_BACKEND_PORT))
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void linshareShouldStart() {
        given()
            .auth().basic("user1@linshare.org", "password1")
        .when()
            .log().uri()
            .get("linshare/webservice/rest/user/v2/documents")
            .prettyPeek()
        .then()
            .statusCode(HttpStatus.SC_OK);
    }
}
