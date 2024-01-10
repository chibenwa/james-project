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

package org.apache.james.mailbox.postgres.mail;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.BODY_BLOB_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.HEADER_CONTENT;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.backends.postgres.utils.PostgresUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.HeaderAndBodyByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.ReactorUtils;
import org.jooq.Record;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import javax.mail.Flags;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMessageIdMapper implements MessageIdMapper {
    private static final Function<MailboxMessage, ByteSource> MESSAGE_BODY_CONTENT_LOADER = (mailboxMessage) -> new ByteSource() {
        @Override
        public InputStream openStream() {
            try {
                return mailboxMessage.getBodyContent();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long size() {
            return mailboxMessage.getBodyOctets();
        }
    };
    public static final int NUM_RETRIES = 5;
    public static final Logger LOGGER = LoggerFactory.getLogger(PostgresMessageIdMapper.class);


    private final PostgresMessageDAO messageDAO;
    private final PostgresMailboxMessageDAO mailboxMessageDAO;
    private final PostgresMailboxDAO mailboxDAO;
    private final PostgresModSeqProvider modSeqProvider;
    private final BlobStore blobStore;
    private final Clock clock;
    private final BlobId.Factory blobIdFactory;

    public PostgresMessageIdMapper(PostgresExecutor postgresExecutor,
                                   PostgresModSeqProvider modSeqProvider,
                                   BlobStore blobStore,
                                   Clock clock,
                                   BlobId.Factory blobIdFactory) {
        this.messageDAO = new PostgresMessageDAO(postgresExecutor, blobIdFactory);
        this.mailboxMessageDAO = new PostgresMailboxMessageDAO(postgresExecutor);
        this.mailboxDAO = new PostgresMailboxDAO(postgresExecutor);
        this.modSeqProvider = modSeqProvider;
        this.blobStore = blobStore;
        this.clock = clock;
        this.blobIdFactory = blobIdFactory;
    }

    @Override
    public List<MailboxMessage> find(Collection<MessageId> messageIds, MessageMapper.FetchType fetchType) {
        return findReactive(messageIds, fetchType)
            .collectList()
            .block();
    }

    @Override
    public Publisher<ComposedMessageIdWithMetaData> findMetadata(MessageId messageId) {
        PostgresMessageId postgresMessageId = (PostgresMessageId) messageId;
        return mailboxMessageDAO.findMetadataByMessageId(postgresMessageId);
    }

    @Override
    public Flux<MailboxMessage> findReactive(Collection<MessageId> messageIds, MessageMapper.FetchType fetchType) {
        return Flux.fromIterable(messageIds)
            .map(PostgresMessageId.class::cast)
            .flatMap(messageId -> mailboxMessageDAO.findMessagesByMessageId(messageId, fetchType), ReactorUtils.DEFAULT_CONCURRENCY)
            .flatMap(messageBuilderAndRecord -> {
                SimpleMailboxMessage.Builder messageBuilder = messageBuilderAndRecord.getLeft();
                if (fetchType == MessageMapper.FetchType.FULL) {
                    return retrieveFullContent(messageBuilderAndRecord.getRight())
                        .map(headerAndBodyContent -> messageBuilder.content(headerAndBodyContent).build());
                }
                return Mono.just(messageBuilder.build());
            }, ReactorUtils.DEFAULT_CONCURRENCY);
    }

    @Override
    public List<MailboxId> findMailboxes(MessageId messageId) {
        PostgresMessageId postgresMessageId = (PostgresMessageId) messageId;
        return mailboxMessageDAO.findMailboxes(postgresMessageId)
            .collectList()
            .block();
    }

    @Override
    public void save(MailboxMessage mailboxMessage) {
        PostgresMailboxId mailboxId = (PostgresMailboxId) mailboxMessage.getMailboxId();
        mailboxMessage.setSaveDate(Date.from(clock.instant()));
        mailboxDAO.findMailboxById(mailboxId)
            .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(mailboxId)))
            .then(saveBodyContent(mailboxMessage))
            .flatMap(blobId -> messageDAO.insert(mailboxMessage, blobId.asString())
                .onErrorResume(PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE, e -> Mono.empty()))
            .then(mailboxMessageDAO.insert(mailboxMessage)
                .onErrorResume(PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE, e -> Mono.empty()))
            .block();
    }

    @Override
    public void copyInMailbox(MailboxMessage mailboxMessage, Mailbox mailbox) {
        copyInMailboxReactive(mailboxMessage, mailbox).block();
    }

    @Override
    public Mono<Void> copyInMailboxReactive(MailboxMessage mailboxMessage, Mailbox mailbox) {
        mailboxMessage.setSaveDate(Date.from(clock.instant()));
        PostgresMailboxId mailboxId = (PostgresMailboxId) mailbox.getMailboxId();
        return mailboxMessageDAO.insert(mailboxMessage, mailboxId)
            .onErrorResume(PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE, e -> Mono.empty());
    }

    @Override
    public void delete(MessageId messageId) {
        PostgresMessageId postgresMessageId = (PostgresMessageId) messageId;
        mailboxMessageDAO.deleteByMessageId(postgresMessageId).block();
    }

    @Override
    public void delete(MessageId messageId, Collection<MailboxId> mailboxIds) {
        deleteReactive(messageId, mailboxIds)
            .block();
    }

    @Override
    public Mono<Void> deleteReactive(MessageId messageId, Collection<MailboxId> mailboxIds) {
        PostgresMessageId postgresMessageId = (PostgresMessageId) messageId;
        return mailboxMessageDAO.deleteByMessageId(postgresMessageId, mailboxIds);
    }

    @Override
    public void delete(Multimap<MessageId, MailboxId> ids) {
        deleteReactive(ids).block();
    }

    @Override
    public Mono<Void> deleteReactive(Multimap<MessageId, MailboxId> ids) {
        return Flux.fromIterable(ids.asMap().entrySet())
            .flatMap(entry -> deleteReactive(entry.getKey(), entry.getValue()), DEFAULT_CONCURRENCY)
            .then();
    }


    @Override
    public Mono<Multimap<MailboxId, UpdatedFlags>> setFlags(MessageId messageId, List<MailboxId> mailboxIds, Flags newState, MessageManager.FlagsUpdateMode updateMode) {
        return Flux.fromIterable(mailboxIds)
            .distinct()
            .map(PostgresMailboxId.class::cast)
            .concatMap(mailboxId -> flagsUpdateWithRetry(newState, updateMode, mailboxId, messageId))
            .collect(ImmutableListMultimap.toImmutableListMultimap(Pair::getLeft, Pair::getRight));
    }

    private Flux<Pair<MailboxId, UpdatedFlags>> flagsUpdateWithRetry(Flags newState, MessageManager.FlagsUpdateMode updateMode, MailboxId mailboxId, MessageId messageId) {
        return updateFlags(mailboxId, messageId, newState, updateMode)
            .retry(NUM_RETRIES)
            .onErrorResume(MailboxDeleteDuringUpdateException.class, e -> {
                LOGGER.info("Mailbox {} was deleted during flag update", mailboxId);
                return Mono.empty();
            })
            .flux()
            .flatMapIterable(Function.identity())
            .map(pair -> buildUpdatedFlags(pair.getRight(), pair.getLeft()));
    }

    private Pair<MailboxId, UpdatedFlags> buildUpdatedFlags(ComposedMessageIdWithMetaData composedMessageIdWithMetaData, Flags oldFlags) {
        return Pair.of(composedMessageIdWithMetaData.getComposedMessageId().getMailboxId(),
            UpdatedFlags.builder()
                .uid(composedMessageIdWithMetaData.getComposedMessageId().getUid())
                .messageId(composedMessageIdWithMetaData.getComposedMessageId().getMessageId())
                .modSeq(composedMessageIdWithMetaData.getModSeq())
                .oldFlags(oldFlags)
                .newFlags(composedMessageIdWithMetaData.getFlags())
                .build());
    }

    private Mono<List<Pair<Flags, ComposedMessageIdWithMetaData>>> updateFlags(MailboxId mailboxId, MessageId messageId, Flags newState, MessageManager.FlagsUpdateMode updateMode) {
        PostgresMailboxId postgresMailboxId = (PostgresMailboxId) mailboxId;
        PostgresMessageId postgresMessageId = (PostgresMessageId) messageId;
        return mailboxMessageDAO.findMetadataByMessageId(postgresMessageId, postgresMailboxId)
            .flatMap(oldComposedId -> updateFlags(newState, updateMode, postgresMailboxId, oldComposedId), ReactorUtils.DEFAULT_CONCURRENCY)
            .switchIfEmpty(Mono.error(MailboxDeleteDuringUpdateException::new))
            .collectList();
    }

    private Mono<Pair<Flags, ComposedMessageIdWithMetaData>> updateFlags(Flags newState, MessageManager.FlagsUpdateMode updateMode, PostgresMailboxId mailboxId, ComposedMessageIdWithMetaData oldComposedId) {
        FlagsUpdateCalculator flagsUpdateCalculator = new FlagsUpdateCalculator(newState, updateMode);
        Flags newFlags = flagsUpdateCalculator.buildNewFlags(oldComposedId.getFlags());
        if (identicalFlags(oldComposedId, newFlags)) {
            return Mono.just(Pair.of(oldComposedId.getFlags(), oldComposedId));
        } else {
            return modSeqProvider.nextModSeqReactive(mailboxId)
                .flatMap(newModSeq -> {
                    // TODO bug?
                    // ModSeq previousModseq = oldComposedId.getModSeq();

                    return updateFlags(mailboxId, flagsUpdateCalculator, newModSeq, oldComposedId.getComposedMessageId().getUid())
                        .map(flags -> Pair.of(flags, new ComposedMessageIdWithMetaData(
                            oldComposedId.getComposedMessageId(),
                            flags,
                            newModSeq,
                            oldComposedId.getThreadId())));
                });
        }
    }

    private Mono<Flags> updateFlags(PostgresMailboxId mailboxId, FlagsUpdateCalculator flagsUpdateCalculator, ModSeq newModSeq, MessageUid uid) {

        switch (flagsUpdateCalculator.getMode()) {
            case ADD:
                return mailboxMessageDAO.addFlags(mailboxId, uid, flagsUpdateCalculator.providedFlags(), newModSeq);
            case REMOVE:
                return mailboxMessageDAO.removeFlags(mailboxId, uid, flagsUpdateCalculator.providedFlags(), newModSeq);
            case REPLACE:
                return mailboxMessageDAO.replaceFlags(mailboxId, uid, flagsUpdateCalculator.providedFlags(), newModSeq);
            default:
                return Mono.error(() -> new RuntimeException("Unknown MessageRange type " + flagsUpdateCalculator.getMode()));
        }
    }

    private boolean identicalFlags(ComposedMessageIdWithMetaData oldComposedId, Flags newFlags) {
        return oldComposedId.getFlags().equals(newFlags);
    }

    private Mono<Content> retrieveFullContent(Record messageRecord) {
        byte[] headerBytes = messageRecord.get(HEADER_CONTENT);
        return Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(),
                blobIdFactory.from(messageRecord.get(BODY_BLOB_ID)),
                SIZE_BASED))
            .map(bodyBytes -> new HeaderAndBodyByteContent(headerBytes, bodyBytes));
    }

    private Mono<BlobId> saveBodyContent(MailboxMessage message) {
        return Mono.fromCallable(() -> MESSAGE_BODY_CONTENT_LOADER.apply(message))
            .flatMap(bodyByteSource -> Mono.from(blobStore.save(blobStore.getDefaultBucketName(), bodyByteSource, LOW_COST)));
    }
}
