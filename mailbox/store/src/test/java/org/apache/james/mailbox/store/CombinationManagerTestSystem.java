/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.store;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;

public abstract class CombinationManagerTestSystem {
    private final StoreMailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;

    public CombinationManagerTestSystem(StoreMailboxManager mailboxManager, MessageIdManager messageIdManager) {
        this.messageIdManager = messageIdManager;
        this.mailboxManager = mailboxManager;
    }

    public StoreMailboxManager getMailboxManager() {
        return mailboxManager;
    }

    public MessageIdManager getMessageIdManager() {
        return messageIdManager;
    }

    public abstract Mailbox createMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException;

    public abstract MessageManager createMessageManager(Mailbox mailbox, MailboxSession session) throws MailboxException;

}
