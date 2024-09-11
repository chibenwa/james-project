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

import static org.apache.james.transport.mailets.WithStorageDirectiveTest.NO_DOMAIN_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collection;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.model.User;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;


public class SubAddressingTest {

    private UsersRepository usersRepository;
    private MailboxManager mailboxManager;
    private SubAddressing testee;

    @BeforeEach
    void setUp() throws MailboxException {
        usersRepository = mock(UsersRepository.class);
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        MailboxSession session = mailboxManager.createSystemSession(Username.of("recipient"));

        mailboxManager.createMailbox(MailboxPath.forUser(Username.of("recipient"), "any"), session);

        testee = new SubAddressing(usersRepository, mailboxManager, session);

    }


    @Test
    void shouldAddStorageDirectiveMatchingDetails() throws Exception {
        testee.init(FakeMailetConfig.builder()
            .build());

        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipient("recipient+any@localhost")
            .build();

        testee.service(mail);

        AttributeName recipient = AttributeName.of("DeliveryPaths_recipient@localhost");
        assertThat(mail.attributes().map(this::unbox))
            .containsOnly(Pair.of(recipient, "any"));
    }

    @Test
    void shouldAddStorageDirectiveMatchingDetailsAndRights() throws Exception {

        testee.init(FakeMailetConfig.builder()
            .build());

        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipient("recipient+any@localhost")
            .build();


        mailboxManager.setRights(
            MailboxPath.inbox(Username.of("recipient")),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                .forUser(Username.of("sender"))
                .rights(MailboxACL.Right.Post)
                .asAddition()
            ),

            mailboxManager.createSystemSession(Username.of("recipient"))
        );


        testee.service(mail);

        AttributeName recipient = AttributeName.of("DeliveryPaths_recipient@localhost");
        assertThat(mail.attributes().map(this::unbox))
            .containsOnly(Pair.of(recipient, "any"));
    }

    Pair<AttributeName, String> unbox(Attribute attribute) {
        Collection<AttributeValue> collection = (Collection<AttributeValue>) attribute.getValue().getValue();
        return Pair.of(attribute.getName(), (String) collection.stream().findFirst().get().getValue());
    }
}
