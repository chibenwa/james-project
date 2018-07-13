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

package org.apache.james.jmap.api.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletionException;

import org.apache.james.jmap.api.access.exceptions.InvalidAccessToken;
import org.junit.jupiter.api.Test;

public interface AccessTokenRepositoryContract {
    AccessToken TOKEN = AccessToken.generate();
    String USERNAME = "username";
    long TTL_IN_MS = 1000;

    AccessTokenRepository accessTokenRepository();

    @Test
    default void validTokenMustBeRetrieved() {
        accessTokenRepository().addToken(USERNAME, TOKEN).join();
        assertThat(accessTokenRepository().getUsernameFromToken(TOKEN).join()).isEqualTo(USERNAME);
    }

    @Test
    default void absentTokensMustBeInvalid() {
        assertThatThrownBy(() -> accessTokenRepository().getUsernameFromToken(TOKEN).join()).isInstanceOf(CompletionException.class);
        assertThatThrownBy(() -> accessTokenRepository().getUsernameFromToken(TOKEN).join()).hasCauseInstanceOf(InvalidAccessToken.class);
    }

    @Test
    default void removedTokensMustBeInvalid() {
        accessTokenRepository().addToken(USERNAME, TOKEN).join();
        accessTokenRepository().removeToken(TOKEN).join();
        assertThatThrownBy(() -> accessTokenRepository().getUsernameFromToken(TOKEN).join()).isInstanceOf(CompletionException.class);
        assertThatThrownBy(() -> accessTokenRepository().getUsernameFromToken(TOKEN).join()).hasCauseInstanceOf(InvalidAccessToken.class);
    }

    @Test
    default void outDatedTokenMustBeInvalid() throws Exception {
        accessTokenRepository().addToken(USERNAME, TOKEN).join();
        Thread.sleep(2 * TTL_IN_MS);
        assertThatThrownBy(() -> accessTokenRepository().getUsernameFromToken(TOKEN).join()).isInstanceOf(CompletionException.class);
        assertThatThrownBy(() -> accessTokenRepository().getUsernameFromToken(TOKEN).join()).hasCauseInstanceOf(InvalidAccessToken.class);
    }

    @Test
    default void addTokenMustThrowWhenUsernameIsNull() {
        assertThatThrownBy(() -> accessTokenRepository().addToken(null, TOKEN))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void addTokenMustThrowWhenUsernameIsEmpty() {
        assertThatThrownBy(() -> accessTokenRepository().addToken("", TOKEN))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void addTokenMustThrowWhenTokenIsNull() {
        assertThatThrownBy(() -> accessTokenRepository().addToken(USERNAME, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void removeTokenTokenMustThrowWhenTokenIsNull() {
        assertThatThrownBy(() -> accessTokenRepository().removeToken(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void getUsernameFromTokenMustThrowWhenTokenIsNull() {
        assertThatThrownBy(() -> accessTokenRepository().getUsernameFromToken(null))
            .isInstanceOf(NullPointerException.class);
    }

}
