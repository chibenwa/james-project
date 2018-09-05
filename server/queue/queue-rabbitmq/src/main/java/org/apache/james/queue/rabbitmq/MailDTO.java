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

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobId;
import org.apache.james.core.MailAddress;
import org.apache.james.util.SerializationUtil;
import org.apache.james.util.streams.Iterators;
import org.apache.mailet.Mail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MailDTO {
    public static MailDTO fromMail(Mail mail, BlobId headerBlobId, BlobId bodyBlobId) {
        return new MailDTO(
            mail.getRecipients().stream()
                .map(MailAddress::asString)
                .collect(ImmutableList.toImmutableList()),
            mail.getName(),
            mail.getSender().asString(),
            mail.getState(),
            mail.getErrorMessage(),
            mail.getLastUpdated().toInstant(),
            serializedAttributes(mail),
            mail.getRemoteAddr(),
            mail.getRemoteHost(),
            SerializationUtil.serialize(mail.getPerRecipientSpecificHeaders()),
            headerBlobId.asString(),
            bodyBlobId.asString());
    }

    public static Map<String, String> serializedAttributes(Mail mail) {
        return Iterators.toStream(mail.getAttributeNames())
            .map(name -> Pair.of(name, SerializationUtil.serialize(mail.getAttribute(name))))
            .collect(ImmutableMap.toImmutableMap(
                Pair::getKey,
                Pair::getValue));
    }

    private ImmutableList<String> recipients;
    private String name;
    private String sender;
    private String state;
    private String errorMessage;
    private Instant lastUpdated;
    private Map<String, String> attributes;
    private String remoteAddr;
    private String remoteHost;
    private String perRecipientHeaders;
    private String headerBlobId;
    private String bodyBlobId;

    private MailDTO(ImmutableList<String> recipients, String name, String sender, String state, String errorMessage,
                    Instant lastUpdated, Map<String, String> attributes, String remoteAddr, String remoteHost,
                    String perRecipientHeaders, String headerBlobId, String bodyBlobId) {
        this.recipients = recipients;
        this.name = name;
        this.sender = sender;
        this.state = state;
        this.errorMessage = errorMessage;
        this.lastUpdated = lastUpdated;
        this.attributes = attributes;
        this.remoteAddr = remoteAddr;
        this.remoteHost = remoteHost;
        this.perRecipientHeaders = perRecipientHeaders;
        this.headerBlobId = headerBlobId;
        this.bodyBlobId = bodyBlobId;
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

    public String getState() {
        return state;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public String getPerRecipientHeaders() {
        return perRecipientHeaders;
    }

    public String getHeaderBlobId() {
        return headerBlobId;
    }

    public String getBodyBlobId() {
        return bodyBlobId;
    }
}
