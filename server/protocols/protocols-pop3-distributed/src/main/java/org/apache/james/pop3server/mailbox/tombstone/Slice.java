package org.apache.james.pop3server.mailbox.tombstone;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

/**
 * This class has been clone from {@link org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.Slice}
 */
public class Slice {
    public static Slice of(Instant sliceStartInstant) {
        return new Slice(sliceStartInstant);
    }

    private static long calculateSliceCount(Slice firstSlice, Instant endAt, Duration windowSize) {
        long timeDiffInSecond =  ChronoUnit.SECONDS.between(firstSlice.getStartSliceInstant(), endAt);
        if (timeDiffInSecond < 0) {
            return 0;
        } else {
            return (timeDiffInSecond / windowSize.getSeconds()) + 1;
        }
    }

    private final Instant startSliceInstant;

    private Slice(Instant startSliceInstant) {
        Preconditions.checkNotNull(startSliceInstant);

        this.startSliceInstant = startSliceInstant;
    }

    public Slice minus(Duration duration) {
        return Slice.of(startSliceInstant.minus(duration));
    }

    public Slice plus(Duration duration) {
        return Slice.of(startSliceInstant.plus(duration));
    }

    public Instant getStartSliceInstant() {
        return startSliceInstant;
    }

    public Stream<Slice> allSlicesTill(Instant endAt, Duration windowSize) {
        long sliceCount = calculateSliceCount(this, endAt, windowSize);
        long sliceWindowSizeInSecond = windowSize.getSeconds();

        return LongStream.range(0, sliceCount)
            .mapToObj(slicePosition -> this.plus(Duration.ofSeconds(sliceWindowSizeInSecond * slicePosition)));
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Slice) {
            Slice slice = (Slice) o;

            return Objects.equals(this.startSliceInstant, slice.startSliceInstant);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(startSliceInstant);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("startSliceInstant", startSliceInstant)
            .toString();
    }
}
