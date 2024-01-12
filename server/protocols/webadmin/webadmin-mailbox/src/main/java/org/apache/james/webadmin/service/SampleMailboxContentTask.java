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
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SampleMailboxContentTask implements Task {
    private static final Logger LOGGER = LoggerFactory.getLogger(SampleMailboxContentTask.class);
    public static final TaskType TYPE = TaskType.of("SampleMailboxContentTask");
    public static final AdditionalInformationDTOModule<SampleMailboxContentTask.AdditionalInformation, SampleMailboxContentTask.AdditionalInformation> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(SampleMailboxContentTask.AdditionalInformation.class)
            .convertToDTO(SampleMailboxContentTask.AdditionalInformation.class)
            .toDomainObjectConverter(a -> a)
            .toDTOConverter((details, type) -> details)
            .typeName(TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    public static TaskDTOModule<SampleMailboxContentTask, SampleMailboxContentTaskDTO> module(MailboxManager mailboxManager, UsersRepository usersRepository) {
        return DTOModule
            .forDomainObject(SampleMailboxContentTask.class)
            .convertToDTO(SampleMailboxContentTaskDTO.class)
            .toDomainObjectConverter(dto -> new SampleMailboxContentTask(usersRepository, mailboxManager, dto.sampling))
            .toDTOConverter((domainObject, typeName) -> new SampleMailboxContentTaskDTO(TYPE.asString(), domainObject.sampling))
            .typeName(TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static class SampleMailboxContentTaskToTask extends TaskFromRequestRegistry.TaskRegistration {
        @Inject
        public SampleMailboxContentTaskToTask(MailboxManager mailboxManager, UsersRepository usersRepository) {
            super(TaskRegistrationKey.of("sampleMailboxContent"),
                request -> new SampleMailboxContentTask(usersRepository, mailboxManager,
                    Optional.ofNullable(request.queryParams("sampling"))
                        .map(Integer::parseInt)
                        .orElse(100)));
        }
    }

    public static class Context {
        private final AtomicLong sampled = new AtomicLong(0);
        private final AtomicLong error = new AtomicLong(0);
        private final AtomicLong userProcessed = new AtomicLong(0);
        private final AtomicLong failedUsers = new AtomicLong(0);
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO {
        private final int sampling;
        private final long sampled;
        private final long userProcessed;
        private final long failedUsers;
        private final long error;
        private final Instant timestamp;

        @JsonCreator
        public AdditionalInformation(@JsonProperty("type") String type,
                                     @JsonProperty("sampling") int sampling,
                                     @JsonProperty("sampled") long sampled,
                                     @JsonProperty("error") long error,
                                     @JsonProperty("userProcessed") long userProcessed,
                                     @JsonProperty("failedUsers") long failedUsers,
                                     @JsonProperty("timestamp") Instant timestamp) {
            this.sampling = sampling;
            this.sampled = sampled;
            this.error = error;
            this.userProcessed = userProcessed;
            this.failedUsers = failedUsers;
            this.timestamp = timestamp;
        }

        public int getSampling() {
            return sampling;
        }

        public long getSampled() {
            return sampled;
        }

        public long getError() {
            return error;
        }

        public long getUserProcessed() {
            return userProcessed;
        }

        public long getFailedUsers() {
            return failedUsers;
        }

        @JsonIgnore
        @Override
        public Instant timestamp() {
            return timestamp;
        }

        @Override
        public String getType() {
            return TYPE.asString();
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }
    }

    public static class SampleMailboxContentTaskDTO implements TaskDTO {
        private final int sampling;

        @JsonCreator
        public SampleMailboxContentTaskDTO(@JsonProperty("type") String type, @JsonProperty("sampling")  int sampling) {
            this.sampling = sampling;
        }

        @Override
        public String getType() {
            return TYPE.asString();
        }

        public int getSampling() {
            return sampling;
        }
    }

    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private final int sampling;
    private final Context context;

    public SampleMailboxContentTask(UsersRepository usersRepository, MailboxManager mailboxManager, int sampling) {
        Preconditions.checkArgument(sampling > 0);

        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
        this.sampling = sampling;
        this.context = new Context();
    }

    @Override
    public Result run() throws InterruptedException {
        return Flux.from(usersRepository.listReactive())
            .concatMap(user -> {
                MailboxSession mailboxSession = mailboxManager.createSystemSession(user);
                return mailboxManager.search(MailboxQuery.privateMailboxesBuilder(mailboxSession).build(), mailboxSession)
                    .flatMap(mailbox -> Mono.from(mailboxManager.getMailboxReactive(mailbox.getId(), mailboxSession))
                        .onErrorResume(MailboxNotFoundException.class, e -> {
                            LOGGER.error("Inconsistent mailbox {} named {} for user {}",
                                mailbox.getMailbox().getMailboxId(), mailbox.getMailbox().getName(), user.asString());
                            return Mono.empty();
                        }), ReactorUtils.DEFAULT_CONCURRENCY)
                    .flatMap(mailbox -> mailbox.listMessagesMetadata(MessageRange.all(), mailboxSession), ReactorUtils.DEFAULT_CONCURRENCY)
                    .filter(message -> new Random().nextInt(sampling) == 0)
                    .flatMap(message -> checkMessage(message, mailboxSession), ReactorUtils.DEFAULT_CONCURRENCY)
                    .reduce(Task::combine)
                    .doOnNext(next -> context.userProcessed.incrementAndGet())
                    .onErrorResume(e -> {
                        LOGGER.error("Consistency check failed for {}", user.asString(), e);
                        context.failedUsers.incrementAndGet();
                        return Mono.just(Result.PARTIAL);
                    });
            }).reduce(Task::combine)
            .block();
    }

    private Mono<Result> checkMessage(ComposedMessageIdWithMetaData message, MailboxSession mailboxSession) {
        return Mono.from(mailboxManager.getMailboxReactive(message.getComposedMessageId().getMailboxId(), mailboxSession))
            .flatMap(mailbox -> Mono.from(mailbox.getMessagesReactive(MessageRange.one(message.getComposedMessageId().getUid()), FetchGroup.FULL_CONTENT, mailboxSession)))
            .doOnSuccess(next -> context.sampled.incrementAndGet())
            .doOnError(next -> {
                LOGGER.error("Failed to read message {} in mailbox {} with uid {}",
                    message.getComposedMessageId().getMessageId().serialize(),
                    message.getComposedMessageId().getMailboxId().serialize(),
                    message.getComposedMessageId().getUid().asLong());
                context.error.incrementAndGet();
            })
            .thenReturn(Result.COMPLETED)
            .onErrorResume(e -> Mono.just(Result.PARTIAL));
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(TYPE.asString(), sampling, context.sampled.get(), context.error.get(),
            context.userProcessed.get(), context.failedUsers.get(), Clock.systemUTC().instant()));
    }
}
