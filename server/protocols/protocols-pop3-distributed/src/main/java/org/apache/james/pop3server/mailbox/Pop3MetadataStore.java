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

package org.apache.james.pop3server.mailbox;

import java.util.Objects;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;


public interface Pop3MetadataStore {
    class StatMetadata {
        private final MessageId messageId;
        private final long size;

        public StatMetadata(MessageId messageId, long size) {
            this.messageId = messageId;
            this.size = size;
        }

        public MessageId getMessageId() {
            return messageId;
        }

        public long getSize() {
            return size;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof StatMetadata) {
                StatMetadata that = (StatMetadata) o;

                return this.size == that.size
                    && Objects.equals(this.messageId, that.messageId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageId, size);
        }

        @Override
        public String toString() {
            return "StatMetadata{" +
                "messageId=" + messageId +
                ", size=" + size +
                '}';
        }
    }

    Publisher<StatMetadata> stat(MailboxId mailboxId);

    Publisher<Void> add(MailboxId mailboxId, StatMetadata statMetadata);

    Publisher<Void> remove(MailboxId mailboxId, StatMetadata statMetadata);

    Publisher<Void> clear(MailboxId mailboxId);
}
