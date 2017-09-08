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

package org.apache.james.mailbox.cassandra.mail.migration;

import javax.inject.Inject;

import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraBlobsDAO;
import org.apache.james.mailbox.model.Attachment;

public class AttachmentV2Migration implements Migration {
    private final CassandraAttachmentDAO attachmentDAOV1;
    private final CassandraAttachmentDAOV2 attachmentDAOV2;
    private final CassandraBlobsDAO blobsDAO;

    @Inject
    public AttachmentV2Migration(CassandraAttachmentDAO attachmentDAOV1,
                                 CassandraAttachmentDAOV2 attachmentDAOV2,
                                 CassandraBlobsDAO blobsDAO) {
        this.attachmentDAOV1 = attachmentDAOV1;
        this.attachmentDAOV2 = attachmentDAOV2;
        this.blobsDAO = blobsDAO;
    }

    @Override
    public MigrationResult run() {
        return attachmentDAOV1.retrieveAll()
            .map(this::migrateAttachment)
            .reduce(MigrationResult.COMPLETED, Migration::combine);
    }

    private MigrationResult migrateAttachment(Attachment attachment) {
        try {
            blobsDAO.save(attachment.getBytes())
                .thenApply(blobId -> CassandraAttachmentDAOV2.from(attachment, blobId))
                .thenCompose(attachmentDAOV2::storeAttachment)
                .thenCompose(any -> attachmentDAOV1.deleteAttachment(attachment.getAttachmentId()))
                .join();
            return MigrationResult.COMPLETED;
        } catch (Exception e) {
            return MigrationResult.PARTIAL;
        }
    }
}
