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

package org.apache.james.blob.aes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Collection;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ThresholdingOutputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.core.JamesFileBackedOutputStream;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class AESBlobStoreDAO implements BlobStoreDAO {
    // For now, aligned with with MimeMessageInputStreamSource file threshold, detailed benchmarking might be conducted to challenge this choice
    public static final int FILE_THRESHOLD_100_KB = 100 * 1024;
    public static final int MAXIMUM_BLOB_SIZE = 100 * 1024 * 1024;
    public static final int FILE_THRESHOLD_100_KB_READ = 512 * 1024;
    private final BlobStoreDAO underlying;
    private final AesGcmHkdfStreaming streamingAead;

    public AESBlobStoreDAO(BlobStoreDAO underlying, CryptoConfig cryptoConfig) {
        this.underlying = underlying;
        this.streamingAead = PBKDF2StreamingAeadFactory.newAesGcmHkdfStreaming(cryptoConfig);
    }

    public JamesFileBackedOutputStream encrypt(InputStream input) throws IOException {
                JamesFileBackedOutputStream encryptedContent = new JamesFileBackedOutputStream("aesencrypt", FILE_THRESHOLD_100_KB);
        try {
            OutputStream outputStream = streamingAead.newEncryptingStream(encryptedContent, PBKDF2StreamingAeadFactory.EMPTY_ASSOCIATED_DATA);
            input.transferTo(outputStream);
            outputStream.close();
            return encryptedContent;
        } catch (Exception e) {
            encryptedContent.reset();
            throw new RuntimeException("Unable to build payload for object storage, failed to encrypt", e);
        } finally {
            encryptedContent.close();
        }
    }

    public InputStream decrypt(InputStream ciphertext) throws IOException {
        // We break symmetry and avoid allocating resources like files as we are not able, in higher level APIs (mailbox) to do resource cleanup.
        try {
            return ByteStreams.limit(
                streamingAead.newDecryptingStream(ciphertext, PBKDF2StreamingAeadFactory.EMPTY_ASSOCIATED_DATA),
                    MAXIMUM_BLOB_SIZE);
        } catch (GeneralSecurityException e) {
            throw new IOException("Incorrect crypto setup", e);
        }
    }

    public Mono<byte[]> decryptReactiveByteSource(ReactiveByteSource ciphertext, BlobId blobId) {
        if (ciphertext.getSize() > MAXIMUM_BLOB_SIZE) {
            throw new RuntimeException(blobId.asString() + " exceeded maximum blob size");
        }

        return Mono.fromCallable(() -> {
                try {
                    JamesFileBackedOutputStream decryptedContent = new JamesFileBackedOutputStream("aes-decrypt-inner", FILE_THRESHOLD_100_KB_READ);
                    try {
                        CountingOutputStream countingOutputStream = new CountingOutputStream(decryptedContent);
                        try (InputStream ciphertextStream = ReactorUtils.toInputStream(Flux.from(ciphertext.getContent()))) {
                            decrypt(ciphertextStream).transferTo(countingOutputStream);
                        }
                        try (InputStream decryptedStream = decryptedContent.asByteSource().openStream()) {
                            return IOUtils.toByteArray(decryptedStream, countingOutputStream.getCount());
                        }
                    } finally {
                        decryptedContent.reset();
                        decryptedContent.close();
                    }
                } catch (OutOfMemoryError error) {
                    LoggerFactory.getLogger(AESBlobStoreDAO.class)
                        .error("OOM reading {}. Blob size read so far {} bytes.", blobId.asString(), ciphertext.getSize());
                    throw new RuntimeException(error);
                }
            });
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        try {
            return decrypt(underlying.read(bucketName, blobId));
        } catch (IOException e) {
            throw new ObjectStoreIOException("Error reading blob " + blobId.asString(), e);
        }
    }

    @Override
    public Publisher<InputStream> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.from(underlying.readReactive(bucketName, blobId))
            .map(Throwing.function(this::decrypt));
    }

    @Override
    public Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.from(underlying.readBytes(bucketName, blobId))
            .map(bytes -> {
                InputStream inputStream = decrypt(new ByteArrayInputStream(bytes));
                try (UnsynchronizedByteArrayOutputStream outputStream = UnsynchronizedByteArrayOutputStream.builder()
                    .setBufferSize(bytes.length + 128)
                    .get()) {
                    IOUtils.copy(inputStream, outputStream);
                    return outputStream.toByteArray();
                }
            });
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(blobId);
        Preconditions.checkNotNull(data);

        return save(bucketName, blobId, new ByteArrayInputStream(data));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(blobId);
        Preconditions.checkNotNull(inputStream);

        return Mono.usingWhen(
            Mono.fromCallable(() -> encrypt(inputStream)),
            fileBackedOutputStream -> Mono.from(underlying.save(bucketName, blobId, fileBackedOutputStream.asByteSource())),
            Throwing.function(pair -> Mono.fromRunnable(Throwing.runnable(pair::reset)).subscribeOn(Schedulers.boundedElastic())))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(e -> new ObjectStoreIOException("Exception occurred while saving bytearray", e));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(blobId);
        Preconditions.checkNotNull(content);

        return Mono.using(content::openStream,
            in -> Mono.from(save(bucketName, blobId, in)),
            Throwing.consumer(InputStream::close))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, BlobId blobId) {
        return underlying.delete(bucketName, blobId);
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return underlying.delete(bucketName, blobIds);
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        return underlying.deleteBucket(bucketName);
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        return underlying.listBuckets();
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName) {
        return underlying.listBlobs(bucketName);
    }
}
