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
package org.apache.james.mailbox.maildir;

import org.apache.james.junit.TemporaryFolderExtension;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

class FullUserMaildirMailboxManagerTest extends MailboxManagerTest<StoreMailboxManager> {
    private static final ImmutableList<MailboxListener.GroupMailboxListener> NO_ADDITIONAL_LISTENERS = ImmutableList.of();

    @Disabled("Maildir is using DefaultMessageId which doesn't support full feature of a messageId, which is an essential" +
        " element of the Vault")
    @Nested
    class HookTests {
    }

    @RegisterExtension
    TemporaryFolderExtension temporaryFolder = new TemporaryFolderExtension();
    
    @Override
    protected StoreMailboxManager provideMailboxManager() {
        try {
            return MaildirMailboxManagerProvider.createMailboxManager("/%fulluser", temporaryFolder.getTemporaryFolder().getTempDir(), NO_ADDITIONAL_LISTENERS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected StoreMailboxManager provideMailboxManager(MailboxListener.GroupMailboxListener mailboxListener) {
        try {
            return MaildirMailboxManagerProvider.createMailboxManager("/%fulluser",
                temporaryFolder.getTemporaryFolder().getTempDir(), ImmutableList.of(mailboxListener));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected EventBus retrieveEventBus(StoreMailboxManager mailboxManager) {
        return mailboxManager.getEventBus();
    }
}
