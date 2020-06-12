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

package org.apache.james.blob.mail;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.util.io.InputStreamConsummer.consume;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.UUID;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.Store;
import org.apache.james.server.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.server.core.MimeMessageInputStreamSource;
import org.apache.james.util.io.BodyOffsetInputStream;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MimeMessageStore implements Store<MimeMessage, MimeMessagePartsId> {
    private final BlobStore blobStore;

    @Inject
    @VisibleForTesting
    public MimeMessageStore(BlobStore blobStore) {
        this.blobStore = blobStore;
    }

    @Override
    public Mono<MimeMessagePartsId> save(MimeMessage mimeMessage) {
        Preconditions.checkNotNull(mimeMessage);

        return computeBodyStartOctet(mimeMessage)
            .flatMap(Throwing.function(bodyStartOctet -> save(bodyStartOctet, mimeMessage)));
    }

    private Mono<MimeMessagePartsId> save(int bodyStartOctet, MimeMessage mimeMessage) throws MessagingException {
        InputStream headerStream = new BoundedInputStream(new MimeMessageInputStream(mimeMessage), bodyStartOctet);
        InputStream bodyStream = new BufferedInputStream(new MimeMessageInputStream(mimeMessage));

        Mono<BlobId> headerIdMono = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), headerStream, SIZE_BASED))
            .subscribeOn(Schedulers.elastic());
        Mono<BlobId> bodyIdMono = Mono.fromCallable(() -> bodyStream.skip(bodyStartOctet))
            .then(Mono.defer(() -> Mono.from(blobStore.save(blobStore.getDefaultBucketName(), bodyStream, SIZE_BASED))))
            .subscribeOn(Schedulers.elastic());

        return Mono.zip(headerIdMono, bodyIdMono,
            (headerId, bodyId) -> MimeMessagePartsId.builder()
                .headerBlobId(headerId)
                .bodyBlobId(bodyId)
                .build());
    }

    private static Mono<Integer> computeBodyStartOctet(MimeMessage mimeMessage) {
        return Mono.fromCallable(() -> {
            try (MimeMessageInputStream inputStream = new MimeMessageInputStream(mimeMessage);
                 BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                 BodyOffsetInputStream bodyOffsetInputStream = new BodyOffsetInputStream(bufferedInputStream)) {

                consume(bodyOffsetInputStream);

                if (bodyOffsetInputStream.getBodyStartOffset() == -1) {
                    return 0;
                }
                return (int) bodyOffsetInputStream.getBodyStartOffset();
            }
        });
    }

    @Override
    public Mono<MimeMessage> read(MimeMessagePartsId blobIds) {
        Preconditions.checkNotNull(blobIds);

        return Mono.fromCallable(() -> toMimeMessage(
            new SequenceInputStream(
                blobStore.read(blobStore.getDefaultBucketName(), blobIds.getHeaderBlobId()),
                blobStore.read(blobStore.getDefaultBucketName(), blobIds.getBodyBlobId()))));
    }

    private MimeMessage toMimeMessage(InputStream inputStream) {
        try {
            return new MimeMessageCopyOnWriteProxy(new MimeMessageInputStreamSource(UUID.randomUUID().toString(), inputStream));
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
