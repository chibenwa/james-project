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

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.Username;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.webadmin.validation.MailboxName;

import com.google.common.base.MoreObjects;

/**
 * Deletes the messages of a mailbox whose message id is also referenced in another mailbox (the reference mailbox).
 *
 * Useful for cleaning up messages left in their original mailbox by a partially applied move.
 */
public class DeleteMessagesReferencedInMailboxTask implements Task {
    public static class Context {
        public static class Snapshot {
            private final long messagesSuccessCount;
            private final long messagesFailedCount;

            private Snapshot(long messagesSuccessCount, long messagesFailedCount) {
                this.messagesSuccessCount = messagesSuccessCount;
                this.messagesFailedCount = messagesFailedCount;
            }

            public long getMessagesSuccessCount() {
                return messagesSuccessCount;
            }

            public long getMessagesFailedCount() {
                return messagesFailedCount;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot that) {
                    return Objects.equals(this.messagesSuccessCount, that.messagesSuccessCount)
                        && Objects.equals(this.messagesFailedCount, that.messagesFailedCount);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(messagesSuccessCount, messagesFailedCount);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("messagesSuccessCount", messagesSuccessCount)
                    .add("messagesFailedCount", messagesFailedCount)
                    .toString();
            }
        }

        private final AtomicLong messagesSuccessCount;
        private final AtomicLong messagesFailedCount;

        public Context() {
            this.messagesSuccessCount = new AtomicLong();
            this.messagesFailedCount = new AtomicLong();
        }

        public void incrementSuccesses() {
            messagesSuccessCount.incrementAndGet();
        }

        public void incrementMessageFails() {
            messagesFailedCount.incrementAndGet();
        }

        public Snapshot snapshot() {
            return new Snapshot(messagesSuccessCount.get(), messagesFailedCount.get());
        }
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private static AdditionalInformation from(Username username, MailboxName mailboxName, MailboxName referenceMailboxName, Context context) {
            Context.Snapshot snapshot = context.snapshot();
            return new AdditionalInformation(username, mailboxName, referenceMailboxName, Clock.systemUTC().instant(),
                snapshot.getMessagesSuccessCount(), snapshot.getMessagesFailedCount());
        }

        private final Username username;
        private final MailboxName mailboxName;
        private final MailboxName referenceMailboxName;
        private final Instant timestamp;
        private final long messagesSuccessCount;
        private final long messagesFailCount;

        public AdditionalInformation(Username username, MailboxName mailboxName, MailboxName referenceMailboxName, Instant timestamp,
                                     long messagesSuccessCount, long messagesFailCount) {
            this.username = username;
            this.mailboxName = mailboxName;
            this.referenceMailboxName = referenceMailboxName;
            this.timestamp = timestamp;
            this.messagesSuccessCount = messagesSuccessCount;
            this.messagesFailCount = messagesFailCount;
        }

        public Username getUsername() {
            return username;
        }

        public MailboxName getMailboxName() {
            return mailboxName;
        }

        public MailboxName getReferenceMailboxName() {
            return referenceMailboxName;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public long getMessagesSuccessCount() {
            return messagesSuccessCount;
        }

        public long getMessagesFailCount() {
            return messagesFailCount;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static final TaskType TASK_TYPE = TaskType.of("DeleteMessagesReferencedInMailboxTask");

    private final Context context;
    private final Username username;
    private final MailboxName mailboxName;
    private final MailboxName referenceMailboxName;
    private final UserMailboxesService userMailboxesService;

    public DeleteMessagesReferencedInMailboxTask(Username username, MailboxName mailboxName, MailboxName referenceMailboxName,
                                                 UserMailboxesService userMailboxesService) {
        this.username = username;
        this.mailboxName = mailboxName;
        this.referenceMailboxName = referenceMailboxName;
        this.userMailboxesService = userMailboxesService;
        this.context = new Context();
    }

    @Override
    public Result run() {
        return userMailboxesService.deleteMessagesReferencedIn(username, mailboxName, referenceMailboxName, context)
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(username, mailboxName, referenceMailboxName, context));
    }

    public Username getUsername() {
        return username;
    }

    public MailboxName getMailboxName() {
        return mailboxName;
    }

    public MailboxName getReferenceMailboxName() {
        return referenceMailboxName;
    }
}
