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

package org.apache.james.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import reactor.core.publisher.Mono;

class OidcJwtTokenVerifierTest {

    private static final String JWKS_URI_PATH = "/auth/realms/realm1/protocol/openid-connect/certs";

    ClientAndServer mockServer;
    @BeforeEach
    public void setUp() {
        mockServer = ClientAndServer.startClientAndServer(0);
        mockServer
            .when(HttpRequest.request().withPath(JWKS_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
    }

    @Test
    void verifyAndClaimShouldReturnClaimValueWhenValidTokenHasKid() {
        Optional<String> email_address = OidcJwtTokenVerifier.verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN, getJwksURL(), "email_address");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(email_address.isPresent()).isTrue();
            softly.assertThat(email_address.get()).isEqualTo("user@domain.org");
        });
    }

    @Test
    void test() throws Exception {
        Optional<String> email_address = OidcJwtTokenVerifier
            .verifySignatureAndExtractClaim("eyJraWQiOiI4TmhGTjluNVVfT3pSNjFjM3JUUzdJS1dnRnF5ZC1MNUpnVDYxMnBNRkVFIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJmOjg1MzNiY2I3LWIyMmMtNDllZS1hNDVjLTk0NDAwODdkZDkxMjo4MTAwMDIwMDAyMDIiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsImlzcyI6Imh0dHBzOlwvXC90ZXN0cy1vcGVyYXRldXIuZXNwYWNlZGVjb25maWFuY2UubXNzYW50ZS5mclwvYXV0aFwvcmVhbG1zXC9tc3NhbnRlIiwidHlwIjoiQmVhcmVyIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiODEwMDAyMDAwMjAyIiwibm9uY2UiOiJGRjNMd2JGdlQzOHJHTHdqek5OYnlPRDBxMlBWU3NlUmpxbzd1RWJjdEs0Iiwic2lkIjoiNDVjZWJiYTktZGEzYy00ZDIxLTlmMDYtNDkxMTNkNjIzNjI2IiwiYWNyIjoiZWlkYXMxIiwiYXpwIjoib3BlcmF0ZXVyLWFwaWxwcyIsImF1dGhfdGltZSI6MTY2Nzg5NzgxMywic2NvcGUiOiJvcGVuaWQgcHJvZmlsZSBlbWFpbCBzY29wZV9hbGwiLCJleHAiOjE2Njc4OTc5MzMsInNlc3Npb25fc3RhdGUiOiI0NWNlYmJhOS1kYTNjLTRkMjEtOWYwNi00OTExM2Q2MjM2MjYiLCJpYXQiOjE2Njc4OTc4MTMsImp0aSI6IjgxNTcyYWUyLTg1NmEtNDg4Yi05N2E4LWQ0N2Q2ZWY0ZDA3YyJ9.c2-dP3EcQSa6rYVHstr2gvqzo3F8oJ1jgOikE6Q640oWp_hq5klWopHCTfbZPP3k0LpaUt8r8mOEOliVKqFVrSRA8AzvtqzNbKiCtjVfflsfPbtjCrahwqkyvzZO6tkW-Lo-3PPiNpNt5MWQEGdR5kggM0q338wfZ2doJmHPOd3fCkhUUJxEtBbXPD5IlJzAgOgeZ_Q84lfD4sFVcvfeZKZRxR6fHHW5bR7wtrOlA31bYcJH8EXDdNHcsg0gl9Ldai1h7b4FgFtBVZ3oys3ic0GyrV_82y88KgSPHu2au0BtVgmKnpIPAFbUMeiLPCcB7Yc4micuK9aXUrzF7azNrUw3EjUCpnOcW6UvcW2ynFRIydKlbttsVyLFWmj4anPXap8kwodtDeqVLMcC4Szlvm6iaNeprxDcf1e6YU15TCRXbEawsKXdUf5ElXzVZILrzPnr202Nwo2dV6i9_6gBxbGYRdFJJ7ur3os9oX2rkKXVXQI9Q_NYDzCxZUNsCp9gKMHcZ6RPpYPH8pY56ecXm2fH2zn6hqQQMIdkW_Rn1UeAdmLs8gTTzPNx9rneTJs9cojwv3ceWYh8UGQV8fyDoO3p_HGv_NjGuN_xe53ITxI2foKQOqCzegz-DiOvMWyrsVjycFPEl7FenL17Elfpk8RTWUQCC7f21xox6POI7P4",
                new URL("https://auth.bas.psc.esante.gouv.fr/auth/realms/esante-wallet/protocol/openid-connect/certs"),
                "preferred_username");

        assertThat(email_address).contains("810002000202");
    }

    @Test
    void test2() throws Exception {
        String email_address = Mono.from(OidcJwtTokenVerifier
            .verifyWithUserInfo("eyJraWQiOiI4TmhGTjluNVVfT3pSNjFjM3JUUzdJS1dnRnF5ZC1MNUpnVDYxMnBNRkVFIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJmOjg1MzNiY2I3LWIyMmMtNDllZS1hNDVjLTk0NDAwODdkZDkxMjo4MTAwMDIwMDAyMDIiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsImlzcyI6Imh0dHBzOlwvXC90ZXN0cy1vcGVyYXRldXIuZXNwYWNlZGVjb25maWFuY2UubXNzYW50ZS5mclwvYXV0aFwvcmVhbG1zXC9tc3NhbnRlIiwidHlwIjoiQmVhcmVyIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiODEwMDAyMDAwMjAyIiwibm9uY2UiOiJGRjNMd2JGdlQzOHJHTHdqek5OYnlPRDBxMlBWU3NlUmpxbzd1RWJjdEs0Iiwic2lkIjoiNDVjZWJiYTktZGEzYy00ZDIxLTlmMDYtNDkxMTNkNjIzNjI2IiwiYWNyIjoiZWlkYXMxIiwiYXpwIjoib3BlcmF0ZXVyLWFwaWxwcyIsImF1dGhfdGltZSI6MTY2Nzg5NzgxMywic2NvcGUiOiJvcGVuaWQgcHJvZmlsZSBlbWFpbCBzY29wZV9hbGwiLCJleHAiOjE2Njc4OTc5MzMsInNlc3Npb25fc3RhdGUiOiI0NWNlYmJhOS1kYTNjLTRkMjEtOWYwNi00OTExM2Q2MjM2MjYiLCJpYXQiOjE2Njc4OTc4MTMsImp0aSI6IjgxNTcyYWUyLTg1NmEtNDg4Yi05N2E4LWQ0N2Q2ZWY0ZDA3YyJ9.c2-dP3EcQSa6rYVHstr2gvqzo3F8oJ1jgOikE6Q640oWp_hq5klWopHCTfbZPP3k0LpaUt8r8mOEOliVKqFVrSRA8AzvtqzNbKiCtjVfflsfPbtjCrahwqkyvzZO6tkW-Lo-3PPiNpNt5MWQEGdR5kggM0q338wfZ2doJmHPOd3fCkhUUJxEtBbXPD5IlJzAgOgeZ_Q84lfD4sFVcvfeZKZRxR6fHHW5bR7wtrOlA31bYcJH8EXDdNHcsg0gl9Ldai1h7b4FgFtBVZ3oys3ic0GyrV_82y88KgSPHu2au0BtVgmKnpIPAFbUMeiLPCcB7Yc4micuK9aXUrzF7azNrUw3EjUCpnOcW6UvcW2ynFRIydKlbttsVyLFWmj4anPXap8kwodtDeqVLMcC4Szlvm6iaNeprxDcf1e6YU15TCRXbEawsKXdUf5ElXzVZILrzPnr202Nwo2dV6i9_6gBxbGYRdFJJ7ur3os9oX2rkKXVXQI9Q_NYDzCxZUNsCp9gKMHcZ6RPpYPH8pY56ecXm2fH2zn6hqQQMIdkW_Rn1UeAdmLs8gTTzPNx9rneTJs9cojwv3ceWYh8UGQV8fyDoO3p_HGv_NjGuN_xe53ITxI2foKQOqCzegz-DiOvMWyrsVjycFPEl7FenL17Elfpk8RTWUQCC7f21xox6POI7P4",
                "preferred_username"))
            .block();

        assertThat(email_address).isEqualTo("810002000202");
    }

    @Test
    void verifyAndClaimShouldReturnClaimValueWhenValidTokenHasNotKid() {
        Optional<String> email_address = OidcJwtTokenVerifier.verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN_HAS_NOT_KID, getJwksURL(), "email_address");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(email_address.isPresent()).isTrue();
            softly.assertThat(email_address.get()).isEqualTo("user@domain.org");
        });
    }

    @Test
    void verifyAndClaimShouldReturnEmptyWhenValidTokenHasNotFoundKid() {
        assertThat(OidcJwtTokenVerifier.verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN_HAS_NOT_FOUND_KID, getJwksURL(), "email_address"))
            .isEmpty();
    }

    @Test
    void verifyAndClaimShouldReturnEmptyWhenClaimNameNotFound() {
        assertThat(OidcJwtTokenVerifier.verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN, getJwksURL(), "not_found"))
            .isEmpty();
    }


    @Test
    void verifyAndClaimShouldReturnEmptyWhenInvalidToken() {
        assertThat(OidcJwtTokenVerifier.verifySignatureAndExtractClaim(OidcTokenFixture.INVALID_TOKEN, getJwksURL(), "email_address"))
            .isEmpty();
    }

    private URL getJwksURL() {
        try {
            return new URL(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), JWKS_URI_PATH));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
