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

package org.apache.james.imap.main;

import java.util.List;

import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class PathConverter {

    public static PathConverter forSession(ImapSession session) {
        return new PathConverter(session);
    }

    private final ImapSession session;

    public PathConverter(ImapSession session) {
        this.session = session;
    }

    public MailboxPath buildFullPath(String mailboxName) {
        if (Strings.isNullOrEmpty(mailboxName)) {
            return new MailboxPath("", "", "");
        }
        if (isAbsolute(mailboxName)) {
            return buildAbsolutePath(mailboxName);
        } else {
            return buildRelativePath(mailboxName);
        }
    }

    private boolean isAbsolute(String mailboxName) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(mailboxName));
        return mailboxName.charAt(0) == MailboxConstants.NAMESPACE_PREFIX_CHAR;
    }

    private MailboxPath buildRelativePath(String mailboxName) {
        return buildMailboxPath(MailboxConstants.USER_NAMESPACE, ImapSessionUtils.getUserName(session), mailboxName);
    }

    private MailboxPath buildAbsolutePath(String absolutePath) {
        char pathDelimiter = ImapSessionUtils.getMailboxSession(session).getPathDelimiter();
        List<String> mailboxPathParts = ImmutableList.copyOf(Splitter.on(pathDelimiter).split(absolutePath));
        String namespace = mailboxPathParts.get(0);
        String mailboxName = Joiner.on(pathDelimiter).join(mailboxPathParts.subList(1, mailboxPathParts.size()));
        if (namespace.equals(MailboxConstants.USER_NAMESPACE)) {
            return buildMailboxPath(mailboxPathParts.get(0), ImapSessionUtils.getUserName(session), mailboxName);
        } else {
            return buildMailboxPath(namespace, null, mailboxName);
        }
    }

    private MailboxPath buildMailboxPath(String namespace, String user, String mailboxName) {
        if (!namespace.equals(MailboxConstants.USER_NAMESPACE)) {
            throw new DeniedSharedMailboxException();
        }
        return new MailboxPath(namespace, user, sanitizeMailboxName(mailboxName));
    }

    private String sanitizeMailboxName(String mailboxName) {
        // use uppercase for INBOX
        // See IMAP-349
        if (mailboxName.equalsIgnoreCase(MailboxConstants.INBOX)) {
            return MailboxConstants.INBOX;
        }
        return mailboxName;
    }

}
