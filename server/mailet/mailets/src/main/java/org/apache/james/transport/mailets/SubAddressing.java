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

package org.apache.james.transport.mailets;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.mail.MessagingException;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.StorageDirective;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class SubAddressing extends GenericMailet {
    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private MailboxSession session;

    @Inject
    public SubAddressing(UsersRepository usersRepository, @Named("mailboxmanager") MailboxManager mailboxManager, MailboxSession session) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
        this.session = session;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        mail.getRecipients().forEach(recipient ->
            recipient.getLocalPartDetails("+")
                .ifPresent(
                    Throwing.consumer(details -> {

                        boolean hasRight = mailboxManager.hasRight(
                            MailboxPath.forUser(
                                Username.fromMailAddress(recipient.stripDetails("+")),
                                details
                            ),
                            MailboxACL.Right.Post,
                            session
                        );

                        String targetFolder = hasRight ? details : "inbox";

                        StorageDirective.builder().targetFolders(ImmutableList.of(targetFolder)).build()
                            .encodeAsAttributes(usersRepository.getUsername(recipient))
                            .forEach(mail::setAttribute);
                    })
                )
        );
    }
}
