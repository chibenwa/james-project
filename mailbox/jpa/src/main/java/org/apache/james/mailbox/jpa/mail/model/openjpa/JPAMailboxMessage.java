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
package org.apache.james.mailbox.jpa.mail.model.openjpa;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Lob;
import javax.persistence.Table;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;

@Entity(name="MailboxMessage")
@Table(name="JAMES_MAIL")
public class JPAMailboxMessage extends AbstractJPAMailboxMessage {

    /** The value for the body field. Lazy loaded */
    /** We use a max length to represent 1gb data. Thats prolly overkill, but who knows */
    @Basic(optional = false, fetch = FetchType.LAZY)
    @Column(name = "MAIL_BYTES", length = 1048576000, nullable = false)
    @Lob private byte[] body;


    /** The value for the header field. Lazy loaded */
    /** We use a max length to represent 10mb data. Thats prolly overkill, but who knows */
    @Basic(optional = false, fetch = FetchType.LAZY)
    @Column(name = "HEADER_BYTES", length = 10485760, nullable = false)
    @Lob private byte[] header;
    
    public JPAMailboxMessage(JPAMailbox mailbox, Message message, Flags flags) throws MailboxException {
        super(mailbox, message.getInternalDate(), flags, message.getFullContentOctets(), (int) (message.getFullContentOctets() - message.getBodyOctets()), new PropertyBuilder(message.getProperties(), message.getTextualLineCount()));
        try {
            int headerEnd = (int) (message.getFullContentOctets() - message.getBodyOctets());
            if (headerEnd < 0) {
                headerEnd = 0;
            }
            SharedByteArrayInputStream content = new SharedByteArrayInputStream(IOUtils.toByteArray(message.getFullContent()));
            this.header = IOUtils.toByteArray(content.newStream(0, headerEnd));
            this.body = IOUtils.toByteArray(content.newStream(getBodyStartOctet(), -1));

        } catch (IOException e) {
            throw new MailboxException("Unable to parse message",e);
        }
    }

    /**
     * Create a copy of the given message
     */
    public JPAMailboxMessage(JPAMailbox mailbox, MessageUid uid, long modSeq, MailboxMessage message) throws MailboxException{
        super(mailbox, uid, modSeq, message);
        try {
            this.body = IOUtils.toByteArray(message.getBodyContent());
            this.header = IOUtils.toByteArray(message.getHeaderContent());
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message",e);
        }
    }

    @Override
    public InputStream getBodyContent() throws IOException {
        return new ByteArrayInputStream(body);
    }

    @Override
    public InputStream getHeaderContent() throws IOException {
        return new ByteArrayInputStream(header);
    }

}
