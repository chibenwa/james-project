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

package org.apache.james;

import java.io.IOException;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import io.netty.buffer.Unpooled;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class TNDemoMailet extends GenericMailet {
    private final MessageParser messageParser;
    private final HttpClient httpClient;

    @Inject
    public TNDemoMailet(MessageParser messageParser) {
        this.messageParser = messageParser;
        this.httpClient = HttpClient.create();
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        if (! addressesToTaxAuthority(mail)) {
            return;
        }
        Optional<ParsedAttachment> parsedAttachment = retrieveDeclaration(mail);
        if (parsedAttachment.isEmpty()) {
            return;
        }

        try {
            httpClient
                .baseUrl("http://172.17.0.2/taxes/declaration/" + mail.getMaybeSender().asString())
                .post()
                .send(Mono.just(Unpooled.wrappedBuffer(parsedAttachment.get().getContent().read())))
                .response()
                .then()
                .block();
        } catch (IOException e) {
            throw new MessagingException("Oups", e);
        }
        mail.setState(Mail.GHOST);
        System.out.println("Reported the tax declaration for " + mail.getMaybeSender().asString());
    }

    private boolean addressesToTaxAuthority(Mail mail) throws MessagingException {
        return mail.getRecipients().size() == 1 && mail.getRecipients().iterator().next().equals(new MailAddress("declaration@impot.gouv.tn"));
    }

    private Optional<ParsedAttachment> retrieveDeclaration(Mail mail) throws MessagingException {
        try {
            MessageParser.ParsingResult parsingResult = messageParser.retrieveAttachments(new MimeMessageInputStream(mail.getMessage()));

            return parsingResult.getAttachments()
                .stream()
                .filter(attachment -> attachment.getName().map("declaration-2023.xls"::equals).orElse(false))
                .findAny();
        } catch (IOException e) {
            throw new MessagingException("Oups", e);
        }
    }
}
