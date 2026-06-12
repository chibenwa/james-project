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

package org.apache.james.protocols.api.sasl;

import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.exception.MailboxException;

import com.google.common.collect.ImmutableList;

/**
 * Shared authorization step of SASL mechanisms: turns an authenticated user and the
 * requested authorization identity into a terminal SASL step.
 */
public final class SaslDelegation {
    private static final String ADMIN_USERS_PATH = "auth.adminUsers.adminUser";

    /**
     * Extends an {@link Authorizator} with the admin users declared in one server configuration block:
     * configured admin users may log in as any other user.
     */
    public static Authorizator withConfiguredAdminUsers(Authorizator authorizator, HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
        ImmutableList<String> adminUsers = ImmutableList.copyOf(serverConfiguration.getStringArray(ADMIN_USERS_PATH));
        return Authorizator.combine(authorizator, (userId, otherUserId) -> {
            if (adminUsers.contains(userId.asString())) {
                return Authorizator.AuthorizationState.ALLOWED;
            }
            return Authorizator.AuthorizationState.FORBIDDEN;
        });
    }

    static SaslStep authorize(Authorizator authorizator, Username authenticatedUser, Username authorizationId) {
        if (authenticatedUser.equals(authorizationId)) {
            return new SaslStep.Success(new SaslIdentity(authenticatedUser, authorizationId), Optional.empty());
        }
        try {
            return switch (authorizator.canLoginAsOtherUser(authenticatedUser, authorizationId)) {
                case ALLOWED -> new SaslStep.Success(new SaslIdentity(authenticatedUser, authorizationId), Optional.empty());
                case FORBIDDEN -> new SaslStep.Failure("Requested delegation is forbidden.",
                    SaslStep.Failure.Cause.DELEGATION_FORBIDDEN, Optional.of(authenticatedUser), Optional.of(authorizationId));
                case UNKNOWN_USER -> new SaslStep.Failure("Delegation target user does not exist.",
                    SaslStep.Failure.Cause.UNKNOWN_USER, Optional.of(authenticatedUser), Optional.of(authorizationId));
            };
        } catch (MailboxException e) {
            return new SaslStep.Failure("Unexpected error while checking delegation",
                SaslStep.Failure.Cause.SERVER_ERROR, Optional.of(authenticatedUser), Optional.of(authorizationId));
        }
    }

    private SaslDelegation() {
    }
}
