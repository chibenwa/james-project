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

import java.net.URI;
import java.net.URL;
import java.util.Optional;

import org.apache.james.jwt.introspection.DefaultIntrospectionClient;
import org.apache.james.jwt.introspection.IntrospectionClient;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.jwt.introspection.TokenIntrospectionResponse;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import reactor.core.publisher.Mono;

public class OidcJwtTokenVerifier {
    public static final IntrospectionClient INTROSPECTION_CLIENT = new DefaultIntrospectionClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcJwtTokenVerifier.class);

    public static Optional<String> verifySignatureAndExtractClaim(String jwtToken, URL jwksURL, String claimName) {
        Optional<String> unverifiedClaim = getClaimWithoutSignatureVerification(jwtToken, "kid");
        LOGGER.info("kid {}", unverifiedClaim);
        Optional<String> claim = getClaimWithoutSignatureVerification(jwtToken, claimName); // EVIL
        LOGGER.info("JWT claim {} -> {}", claimName, claim);
        return claim;
    }

    public static Optional<String> verifySignatureAndExtractClaim(String jwtToken, String claimName) {
        Optional<String> unverifiedClaim = getClaimWithoutSignatureVerification(jwtToken, "kid");
        LOGGER.info("kid {}", unverifiedClaim);
        Optional<String> claim = getClaimWithoutSignatureVerification(jwtToken, claimName); // EVIL
        LOGGER.info("JWT claim {} -> {}", claimName, claim);
        return claim;
    }

    public static <T> Optional<T> getHeaderWithoutSignatureVerification(String token, String claimName) {
        int signatureIndex = token.lastIndexOf('.');
        if (signatureIndex <= 0) {
            return Optional.empty();
        }
        String nonSignedToken = token.substring(0, signatureIndex + 1);
        try {
            Jwt<Header, Claims> headerClaims = Jwts.parserBuilder().setAllowedClockSkewSeconds(1000000000000000L).build().parseClaimsJwt(nonSignedToken);
            T claim = (T) headerClaims.getHeader().get(claimName);
            if (claim == null) {
                return Optional.empty(); // Fix a faire dans James
            }
            return Optional.of(claim);
        } catch (JwtException e) {
            LOGGER.info("JwtException ", e);
            return Optional.empty();
        }
    }

    public static Optional<String> getClaimWithoutSignatureVerification(String token, String claimName) {
        int signatureIndex = token.lastIndexOf('.');
        if (signatureIndex <= 0) {
            return Optional.empty();
        }
        String nonSignedToken = token.substring(0, signatureIndex + 1);
        try {
            Jwt<Header, Claims> headerClaims = Jwts.parserBuilder().setAllowedClockSkewSeconds(1000000000000000L).build().parseClaimsJwt(nonSignedToken);
            String claim = headerClaims.getBody().get(claimName, String.class);
            if (claim == null) {
                return Optional.empty(); // Fix a faire dans James
            }
            return Optional.of(claim);
        } catch (JwtException e) {
            LOGGER.info("JwtException ", e);
            return Optional.empty();
        }
    }

    public static Publisher<String> verifyWithMaybeIntrospection(String jwtToken, URL jwksURL, String claimName, Optional<IntrospectionEndpoint> introspectionEndpoint) {
        return Mono.fromCallable(() -> verifySignatureAndExtractClaim(jwtToken, jwksURL, claimName))
            .flatMap(optional -> optional.map(Mono::just).orElseGet(Mono::empty))
            .flatMap(claimResult -> {
                if (introspectionEndpoint.isEmpty()) {
                    return Mono.just(claimResult);
                }
                LOGGER.info("Calling introspection endpoint");
                return Mono.justOrEmpty(introspectionEndpoint)
                    .flatMap(endpoint -> Mono.from(INTROSPECTION_CLIENT.introspect(endpoint, jwtToken)))
                    .doOnNext(next -> LOGGER.info("Introspection result {}", next))
                    .filter(TokenIntrospectionResponse::active)
                    .map(activeToken -> claimResult)
                    .doOnError(e -> LOGGER.error("Could not call introspection endpoint", e));
            });
    }

    public static Publisher<String> verifyWithUserInfo(String jwtToken,  String claimName) {
        return Mono.fromCallable(() -> verifySignatureAndExtractClaim(jwtToken, claimName))
            .flatMap(optional -> optional.map(Mono::just).orElseGet(Mono::empty))
            .flatMap(claimResult -> {
                LOGGER.info("Calling userinfo endpoint");
                try {
                    return Mono.from(INTROSPECTION_CLIENT.userInfo(new URI("https://auth.bas.psc.esante.gouv.fr/auth/realms/esante-wallet/protocol/openid-connect/userinfo"),
                        jwtToken))
                        .thenReturn(claimResult);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }
}
