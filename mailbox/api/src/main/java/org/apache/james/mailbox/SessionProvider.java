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

package org.apache.james.mailbox;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;

public interface SessionProvider {
    interface AuthorizationStep {
        MailboxSession as(Username other) throws MailboxException;

        MailboxSession withoutDelegation() throws MailboxException;
    }

    /**
     * Creates a new system session.<br>
     * A system session is intended to be used for programmatic access.<br>
     *
     * Use {@link #authenticate(Username)} when accessing this API from a
     * protocol.
     *
     * @param userName
     *            the name of the user whose session is being created
     * @return <code>MailboxSession</code>, not null
     */
    MailboxSession createSystemSession(Username userName);

    /**
     * Authenticates the given user against the given password,
     * then switch to another user.<br>
     * When authenticated and authorized, a session for the other user will be supplied
     *
     * @param givenUserid
     *            username of the given user, matching the credentials
     * @param passwd
     *            password supplied for the given user
     * @return a <code>MailboxSession</code> for the real user
     *            when the given user is authenticated and authorized to access
     * @throws MailboxException
     *             when the creation fails for other reasons
     */

    AuthorizationStep authenticate(Username givenUserid, String passwd);

    /**
     * Checking given user can log in as another user
     * When delegated and authorized, a session for the other user will be supplied
     *
     * @param givenUserid
     *            username of the given user
     * @return a <code>MailboxSession</code> for the real user
     *            when the given user is authenticated and authorized to access
     * @throws MailboxException
     *             when the creation fails for other reasons
     */
    AuthorizationStep authenticate(Username givenUserid);

    /**
     * <p>
     * Logs the session out, freeing any resources. Clients who open session
     * should make best efforts to call this when the session is closed.
     * </p>
     * <p>
     * Note that clients may not always be able to call logout (whether forced
     * or not). Mailboxes that create sessions which are expensive to maintain
     * <code>MUST</code> retain a reference and periodically check
     * {@link MailboxSession#isOpen()}.
     * </p>
     * <p>
     * Note that implementations much be aware that it is possible that this
     * method may be called more than once with the same session.
     * </p>
     *
     * @param session
     *            not null
     */
    void logout(MailboxSession session);
}
