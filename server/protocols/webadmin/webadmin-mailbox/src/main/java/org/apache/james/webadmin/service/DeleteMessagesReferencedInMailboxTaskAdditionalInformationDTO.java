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

package org.apache.james.webadmin.service;

import java.time.Instant;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.webadmin.validation.MailboxName;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteMessagesReferencedInMailboxTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static final AdditionalInformationDTOModule<DeleteMessagesReferencedInMailboxTask.AdditionalInformation, DeleteMessagesReferencedInMailboxTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(DeleteMessagesReferencedInMailboxTask.AdditionalInformation.class)
            .convertToDTO(DeleteMessagesReferencedInMailboxTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(DeleteMessagesReferencedInMailboxTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(DeleteMessagesReferencedInMailboxTaskAdditionalInformationDTO::toDto)
            .typeName(DeleteMessagesReferencedInMailboxTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private static DeleteMessagesReferencedInMailboxTask.AdditionalInformation toDomainObject(DeleteMessagesReferencedInMailboxTaskAdditionalInformationDTO dto) {
        return new DeleteMessagesReferencedInMailboxTask.AdditionalInformation(
            Username.of(dto.getUsername()),
            new MailboxName(dto.getMailboxName()),
            new MailboxName(dto.getReferenceMailboxName()),
            dto.getTimestamp(),
            dto.getMessagesSuccessCount(),
            dto.getMessagesFailCount());
    }

    private static DeleteMessagesReferencedInMailboxTaskAdditionalInformationDTO toDto(DeleteMessagesReferencedInMailboxTask.AdditionalInformation domain, String type) {
        return new DeleteMessagesReferencedInMailboxTaskAdditionalInformationDTO(
            type,
            domain.getUsername().asString(),
            domain.getMailboxName().asString(),
            domain.getReferenceMailboxName().asString(),
            domain.getTimestamp(),
            domain.getMessagesSuccessCount(),
            domain.getMessagesFailCount());
    }

    private final String type;
    private final String username;
    private final String mailboxName;
    private final String referenceMailboxName;
    private final Instant timestamp;
    private final long messagesSuccessCount;
    private final long messagesFailCount;

    public DeleteMessagesReferencedInMailboxTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                         @JsonProperty("username") String username,
                                                                         @JsonProperty("mailboxName") String mailboxName,
                                                                         @JsonProperty("referenceMailboxName") String referenceMailboxName,
                                                                         @JsonProperty("timestamp") Instant timestamp,
                                                                         @JsonProperty("messagesSuccessCount") long messagesSuccessCount,
                                                                         @JsonProperty("messagesFailCount") long messagesFailCount) {
        this.type = type;
        this.username = username;
        this.mailboxName = mailboxName;
        this.referenceMailboxName = referenceMailboxName;
        this.timestamp = timestamp;
        this.messagesSuccessCount = messagesSuccessCount;
        this.messagesFailCount = messagesFailCount;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public String getUsername() {
        return username;
    }

    public String getMailboxName() {
        return mailboxName;
    }

    public String getReferenceMailboxName() {
        return referenceMailboxName;
    }

    public long getMessagesSuccessCount() {
        return messagesSuccessCount;
    }

    public long getMessagesFailCount() {
        return messagesFailCount;
    }
}
