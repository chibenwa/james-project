package org.apache.james.server.blob.deduplication;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class BloomFilterGCAlgorithm {
    private final BlobReferenceSource referenceSource;
    private final BlobStore blobStore;
    private final BucketName bucketName;

    public BloomFilterGCAlgorithm(BlobReferenceSource referenceSource, BlobStore blobStore, BucketName bucketName) {
        this.referenceSource = referenceSource;
        this.blobStore = blobStore;
        this.bucketName = bucketName;
    }

    public Mono<Void> gc(int expectedBlobCount, double associatedProbability) {
        // Avoids two subsequent run to have the same false positives.
        String salt = UUID.randomUUID().toString();

        return populatedBloomFilter(salt, expectedBlobCount, associatedProbability)
            .flatMap(bloomFilter -> Flux.from(blobStore.listBlobs(bucketName))
                .filter(blobId -> !bloomFilter.mightContain(salt + blobId.asString()))
                .flatMap(orphanBlobId -> blobStore.delete(bucketName, orphanBlobId))
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
