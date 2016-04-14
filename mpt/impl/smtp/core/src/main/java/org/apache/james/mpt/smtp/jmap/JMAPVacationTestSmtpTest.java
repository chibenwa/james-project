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
package org.apache.james.mpt.smtp.jmap;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.Matchers.equalTo;

import java.net.InetAddress;
import java.util.Locale;

import javax.inject.Inject;

import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mpt.script.AbstractSimpleScriptedTestProtocol;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.GenericContainer;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public class JMAPVacationTestSmtpTest extends AbstractSimpleScriptedTestProtocol {

    public static final String USER = "bob";
    public static final String DOMAIN = "mydomain.tld";
    public static final String USER_AT_DOMAIN = USER + "@" + DOMAIN;
    public static final String PASSWORD = "secret";
    public static final String HTTP_LOCALHOST = "http://127.0.0.1";
    public static final String YOPMAIL_COM = "yopmail.com";
    public static final String MYDOMAIN_TLD = "mydomain.tld";
    public static final String LOCALHOST = "127.0.0.1";
    public static final String REASON = "Message explaining my wonderful vacations";

    private final TemporaryFolder folder = new TemporaryFolder();
    private final GenericContainer fakeSmtp = new GenericContainer("weave/rest-smtp-sink:latest");

    @Rule
    public final RuleChain chain = RuleChain.outerRule(folder).around(fakeSmtp);

    @Inject
    private static JmapSmtpHostSystem hostSystem;
    private int restSMTPPort;
    private String restSMTPBasedUPI;

    public JMAPVacationTestSmtpTest() throws Exception {
        super(hostSystem, USER_AT_DOMAIN, PASSWORD, "/org/apache/james/smtp/scripts/");
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        InetAddress containerIp = InetAddresses.forString(fakeSmtp.getContainerInfo().getNetworkSettings().getIpAddress());
        hostSystem.getInMemoryDnsService()
            .registerRecord(YOPMAIL_COM, new InetAddress[]{containerIp}, ImmutableList.of(YOPMAIL_COM), ImmutableList.of());
        hostSystem.getInMemoryDnsService()
            .registerRecord(DOMAIN, new InetAddress[]{InetAddresses.forString(LOCALHOST)}, ImmutableList.of(MYDOMAIN_TLD), ImmutableList.of());

        restSMTPPort = 80;
        restSMTPBasedUPI = "http://" + containerIp.getHostAddress();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));
    }

    @Test
    public void jmapVacationShouldWork() throws Exception {
        String bodyRequest = "[[" +
            "\"setVacationResponse\", " +
            "{" +
                "\"update\":{" +
                    "\"singleton\" : {" +
                        "\"id\": \"singleton\"," +
                        "\"isEnabled\": \"true\"," +
                        "\"textBody\": \"" + REASON + "\"" +
                    "}" +
                "}" +
            "}, \"#0\"" +
            "]]";

        RestAssured.port = hostSystem.getJmapPort();
        RestAssured.baseURI = HTTP_LOCALHOST;

        AccessToken accessToken = JmapAuthentication.authenticateJamesUser(USER_AT_DOMAIN, PASSWORD);

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(bodyRequest)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        scriptTest("helo", Locale.US);

        Thread.sleep(1000);

        RestAssured.port = restSMTPPort;
        RestAssured.baseURI = restSMTPBasedUPI;

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
        .when()
            .get("/api/email")
            .prettyPeek()
        .then()
            .statusCode(200)
            .body("[0].from", equalTo(USER_AT_DOMAIN))
            .body("[0].to[0]", equalTo("matthieu@yopmail.com"))
            .body("[0].subject", equalTo(USER_AT_DOMAIN + " is currently in vacation"))
            .body("[0].text", equalTo(REASON));
    }
}
