package org.apache.james.server.blob.deduplication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.apache.james.blob.api.BlobId;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.io.ByteSource;

public class GenerationAwareBlobId implements BlobId {
    // TODO complete this class
    public static class Factory implements BlobId.Factory {
        private final Clock clock;
        private final BlobId.Factory delegate;
        private final Duration generationDuration;
        private final int generationFamily;

        public Factory(Clock clock, BlobId.Factory delegate, Duration generationDuration, int generationFamily) {
            this.clock = clock;
            this.delegate = delegate;
            this.generationDuration = generationDuration;
            this.generationFamily = generationFamily;
        }

        // TODO complete me
        @Override
        public BlobId forPayload(byte[] payload) {
            return new GenerationAwareBlobId(0, generationFamily, delegate.forPayload(payload));
        }

        // TODO complete me
        @Override
        public BlobId forPayload(ByteSource payload) {
            return new GenerationAwareBlobId(0, generationFamily, delegate.forPayload(payload));
        }

        // TODO complete me
        @Override
        public BlobId from(String id) {
            return new GenerationAwareBlobId(0, generationFamily, delegate.from(id));
        }

        // TODO complete me
        @Override
        public BlobId randomId() {
            return new GenerationAwareBlobId(0, generationFamily, delegate.randomId());
        }
    }

    private final int generation;
    private final int generationFamily;
    private final BlobId delegate;

    public GenerationAwareBlobId(int generation, int generationFamily, BlobId delegate) {
        this.generation = generation;
        this.generationFamily = generationFamily;
        this.delegate = delegate;
    }

    @Override
    public String asString() {
        return generation + "_" + delegate.asString();
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof GenerationAwareBlobId) {
            GenerationAwareBlobId other = (GenerationAwareBlobId) obj;
            return Objects.equal(generation, other.generation)
                && Objects.equal(delegate, other.delegate);
        }
        return false;
    }

    // TODO complete me
    public boolean inActiveGeneration(int family, Instant now) {
        return family == this.generationFamily && false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(generation, delegate);
    }

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("generation", generation)
            .add("delegate", delegate)
            .toString();
    }


}
