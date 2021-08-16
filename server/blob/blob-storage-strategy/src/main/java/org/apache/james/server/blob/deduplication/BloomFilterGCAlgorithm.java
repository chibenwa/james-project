package org.apache.james.server.blob.deduplication;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class BloomFilterGCAlgorithm {
    private final BlobReferenceSource referenceSource;
    private final BlobStore blobStore;
    private final BlobStoreDAO blobStoreDAO;
    private final Clock clock;
    private final BucketName bucketName;
    private final int currentGenerationFamily;

    public BloomFilterGCAlgorithm(BlobReferenceSource referenceSource, BlobStore blobStore, BlobStoreDAO blobStoreDAO, Clock clock, BucketName bucketName, int currentGenerationFamily) {
        this.referenceSource = referenceSource;
        this.blobStore = blobStore;
        this.blobStoreDAO = blobStoreDAO;
        this.clock = clock;
        this.bucketName = bucketName;
        this.currentGenerationFamily = currentGenerationFamily;
    }

    // TODO Context
    //   - How many blob reference do we have?
    //   - How many blobs do we have?
    //   - How many orphan blobs did we encounter?
    //   - How many errors did we encounter?
    // TODO Return a Task.Result
    public Mono<Void> gc(int expectedBlobCount, double associatedProbability) {
        // Avoids two subsequent run to have the same false positives.
        String salt = UUID.randomUUID().toString();
        Instant now = clock.instant();

        return populatedBloomFilter(salt, expectedBlobCount, associatedProbability)
            .flatMap(bloomFilter -> Flux.from(blobStore.listBlobs(bucketName))
                .map(GenerationAwareBlobId.class::cast)
                .filter(blobId -> !blobId.inActiveGeneration(currentGenerationFamily, now))
                .filter(blobId -> !bloomFilter.mightContain(salt + blobId.asString()))
                .flatMap(orphanBlobId -> blobStoreDAO.delete(bucketName, orphanBlobId))
                .then());
    }

    private Mono<BloomFilter<CharSequence>> populatedBloomFilter(String salt, int expectedBlobCount, double associatedProbability) {
        return Mono.fromCallable(() -> BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.US_ASCII),
                expectedBlobCount, associatedProbability))
            .flatMap(bloomFilter -> Flux.from(referenceSource.listReferencedBlobs())
                .map(ref -> bloomFilter.put(salt + ref.asString()))
                .then()
                .thenReturn(bloomFilter));
    }

}
