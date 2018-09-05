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

package org.apache.james.queue.rabbitmq;

import java.util.Collection;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class MailDTO {
    public static MailDTO fromMail(Mail mail) {
        return new MailDTO(
            mail.getRecipients().stream()
                .map(MailAddress::asString)
                .collect(ImmutableList.toImmutableList()),
            mail.getName(),
            mail.getSender().asString());
    }

    private ImmutableList<String> recipients;
    private String name;
    private String sender;

    @JsonCreator
    private MailDTO(@JsonProperty("recipients") ImmutableList<String> recipients,
                    @JsonProperty("name") String name,
                    @JsonProperty("sender") String sender) {
        this.recipients = recipients;
        this.name = name;
        this.sender = sender;
    }

    public Collection<String> getRecipients() {
        return recipients;
    }

    public String getName() {
        return name;
    }

    public String getSender() {
        return sender;
    }
}
