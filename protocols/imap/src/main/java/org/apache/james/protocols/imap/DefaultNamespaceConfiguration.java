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

package org.apache.james.protocols.imap;

import java.util.List;

import org.apache.james.imap.api.process.ImapSession;

import com.google.common.collect.ImmutableList;

public class DefaultNamespaceConfiguration implements ImapSession.NamespaceConfiguration {
    public static final String DEFAULT_PERSONAL_NAMESPACE = "";
    public static final String DELEGATED_MAILBOXES_BASE = "Other users";

    @Override
    public String personalNamespace() {
        return DEFAULT_PERSONAL_NAMESPACE;
    }

    @Override
    public String otherUsersNamespace() {
        return DELEGATED_MAILBOXES_BASE;
    }

    @Override
    public List<String> sharedNamespacesNamespaces() {
        return ImmutableList.of();
    }
}
