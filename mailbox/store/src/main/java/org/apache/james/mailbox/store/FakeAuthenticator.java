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
package org.apache.james.mailbox.store;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.core.User;

public class FakeAuthenticator implements Authenticator {

    private final Map<User, String> users = new HashMap<>();

    @Override
    public boolean isAuthentic(User user, CharSequence passwd) {
        String pass = users.get(user);
        if (pass != null) {
            return passwd.toString().equals(pass);
        }
        return false;
    }
    
    public void addUser(User user, String password) {
        users.put(user, password);
    }

    public void clear() {
        users.clear();
    }
}
