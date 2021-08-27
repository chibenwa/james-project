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

package org.apache.james.adapter.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserRepositoryAuthenticatorTest {

    public static final String PASSWORD = "password";
    public static final Username USER = Username.of("user");
    public static final Username USER_WITH_DOMAIN = Username.of("user@domain.com");
    public static final String BAD_PASSWORD = "badPassword";
    public static final Username BAD_USER = Username.of("badUser");
    private UsersRepository usersRepository;
    private UserRepositoryAuthenticator testee;

    @BeforeEach
    void setUp() {
        usersRepository = mock(UsersRepository.class);
        testee = new UserRepositoryAuthenticator(usersRepository);
    }

    @Test
    void isAuthenticShouldReturnTrueWhenGoodLoginPassword() throws Exception {
        when(usersRepository.getUserByName(USER)).thenReturn(new User() {
            @Override
            public Username getUserName() {
                return USER;
            }

            @Override
            public boolean verifyPassword(CharSequence pass) {
                return true;
            }

            @Override
            public boolean setPassword(String newPass) {
                throw new RuntimeException();
            }
        });

        assertThat(testee.isAuthentic(USER, PASSWORD)).contains(USER);
    }

    @Test
    void isAuthenticShouldReturnTranslatedUser() throws Exception {
        when(usersRepository.getUserByName(USER)).thenReturn(new User() {
            @Override
            public Username getUserName() {
                return USER_WITH_DOMAIN;
            }

            @Override
            public boolean verifyPassword(CharSequence pass) {
                return true;
            }

            @Override
            public boolean setPassword(String newPass) {
                throw new RuntimeException();
            }
        });

        assertThat(testee.isAuthentic(USER, BAD_PASSWORD)).contains(USER_WITH_DOMAIN);
    }

    @Test
    void isAuthenticShouldReturnFalseWhenWrongPassword() throws Exception {
        when(usersRepository.getUserByName(USER)).thenReturn(new User() {
            @Override
            public Username getUserName() {
                return USER;
            }

            @Override
            public boolean verifyPassword(CharSequence pass) {
                return false;
            }

            @Override
            public boolean setPassword(String newPass) {
                throw new RuntimeException();
            }
        });

        assertThat(testee.isAuthentic(USER, BAD_PASSWORD)).contains(USER_WITH_DOMAIN);
    }

    @Test
    void isAuthenticShouldReturnFalseWhenBadUser() throws Exception {
        when(usersRepository.getUserByName(USER)).thenReturn(null);

        assertThat(testee.isAuthentic(BAD_USER, BAD_PASSWORD)).isEmpty();
    }

    @Test
    void isAuthenticShouldFailOnUserRepositoryFailure() throws Exception {
        when(usersRepository.test(USER, PASSWORD)).thenThrow(new UsersRepositoryException(""));

        assertThatThrownBy(() -> testee.isAuthentic(USER, PASSWORD))
            .isInstanceOf(MailboxException.class);
    }

}
