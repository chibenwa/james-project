package org.apache.james.pop3server.mailbox.tombstone;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.pop3server.mailbox.Pop3MetadataStore;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Pop3TimeSerieMetadataStore implements Pop3MetadataStore {
    public static final Logger LOGGER = LoggerFactory.getLogger(Pop3TimeSerieMetadataStore.class);
    private static final Duration CLOCK_SKEW_DEFENSE = Duration.ofMinutes(1);

    private final Clock clock;
    private final TimeSerieStartDAO sliceMailboxDAO;
    private final TimeSerieLookupDAO timeSerieLookupDAO;
    private final Pop3TimeSerieDAO timeSerieDAO;

    @Inject
    public Pop3TimeSerieMetadataStore(Clock clock, TimeSerieStartDAO sliceMailboxDAO, TimeSerieLookupDAO timeSerieLookupDAO, Pop3TimeSerieDAO timeSerieDAO) {
        this.clock = clock;
        this.sliceMailboxDAO = sliceMailboxDAO;
        this.timeSerieLookupDAO = timeSerieLookupDAO;
        this.timeSerieDAO = timeSerieDAO;
    }

    @Override
    public Publisher<FullMetadata> stat(MailboxId mailboxId) {
        CassandraId cassandraId = (CassandraId) mailboxId;

        return sliceMailboxDAO.retrieve(cassandraId)
            .map(Optional::of)
            .switchIfEmpty(Mono.just(Optional.empty()))
            .flatMapMany(maybeSlice -> maybeSlice
                .map(slice -> timeSerieDAO.stat(cassandraId, slice.minus(CLOCK_SKEW_DEFENSE)))
                .orElse(timeSerieDAO.statMailbox(cassandraId)))
            .collectList()
            .flatMapMany(metadata -> updateStart(cassandraId, metadata))
            .map(Pop3TimeSerieDAO.TimeSerieStatMetadata::getFullMetadata);
    }

    private Publisher<Pop3TimeSerieDAO.TimeSerieStatMetadata> updateStart(CassandraId cassandraId, Collection<Pop3TimeSerieDAO.TimeSerieStatMetadata> metadata) {
        Optional<Instant> maybeStart = metadata.stream()
            .min(Comparator.comparing(Pop3TimeSerieDAO.TimeSerieStatMetadata::getInstant))
            .map(Pop3TimeSerieDAO.TimeSerieStatMetadata::getInstant);

        return maybeStart
            .map(start -> sliceMailboxDAO.save(cassandraId, Slice.of(start)))
            .orElse(Mono.empty())
            .thenMany(Flux.fromIterable(metadata));
    }

    //@Override
    public Publisher<FullMetadata> retrieveByMailboxIdAndDateBefore(MailboxId mailboxId, Date dateBefore) {
        CassandraId cassandraId = (CassandraId) mailboxId;

        return sliceMailboxDAO.retrieve(cassandraId)
            .map(Optional::of)
            .switchIfEmpty(Mono.just(Optional.empty()))
            .flatMapMany(maybeSlice -> maybeSlice
                .map(slice -> timeSerieDAO.readBefore(cassandraId, dateBefore, Date.from(slice.minus(CLOCK_SKEW_DEFENSE).getStartSliceInstant())))
                .orElse(timeSerieDAO.readBefore(cassandraId, dateBefore)))
            .collectList()
            .flatMapMany(metadata -> updateStart(cassandraId, metadata))
            .map(Pop3TimeSerieDAO.TimeSerieStatMetadata::getFullMetadata);
    }

    @Override
    public Publisher<FullMetadata> listAllEntries() {
        return timeSerieDAO.listAll();
    }

    @Override
    public Publisher<FullMetadata> retrieve(MailboxId mailboxId, MessageId messageId) {
        CassandraId cassandraId = (CassandraId) mailboxId;
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;

        return timeSerieLookupDAO.retrieve(cassandraId, cassandraMessageId)
            .flatMap(slice -> timeSerieDAO.retrieve(cassandraId, slice, cassandraMessageId));
    }

    @Override
    public Publisher<Void> add(MailboxId mailboxId, StatMetadata statMetadata) {
        CassandraId cassandraId = (CassandraId) mailboxId;
        CassandraMessageId messageId = (CassandraMessageId) statMetadata.getMessageId();

        return Mono.fromCallable(clock::instant)
            .map(Slice::of)
            .flatMap(now -> timeSerieLookupDAO.save(cassandraId, messageId, now)
                .then(timeSerieDAO.save(cassandraId, now, statMetadata)))
            .then();
    }

    @Override
    public Publisher<Void> remove(MailboxId mailboxId, MessageId messageId) {
        CassandraId cassandraId = (CassandraId) mailboxId;
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;

        return timeSerieLookupDAO.retrieve(cassandraId, cassandraMessageId)
            // The slice of the entry is not to be found in our lookup table
            // default to a scan to find its value.
            // Note than under normal operations this is not to be happening, but could happen upon inconsistent deletes
            // at the Cassandra level (too low gc_grace_seconds, manual deletes)
            .switchIfEmpty(fallbackToMailboxScan(messageId, cassandraId))
            .flatMap(instant -> timeSerieDAO.delete(cassandraId, instant, cassandraMessageId))
            .then(timeSerieLookupDAO.delete(cassandraId, cassandraMessageId));
    }

    // Double deletes can cause us to mistakenly look for inconsistency
    // Resource waste.
    //@Override
    public Publisher<Void> removeNoFallback(MailboxId mailboxId, MessageId messageId) {
        CassandraId cassandraId = (CassandraId) mailboxId;
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;

        return timeSerieLookupDAO.retrieve(cassandraId, cassandraMessageId)
            .flatMap(instant -> timeSerieDAO.delete(cassandraId, instant, cassandraMessageId))
            .then(timeSerieLookupDAO.delete(cassandraId, cassandraMessageId));
    }

    private Mono<Slice> fallbackToMailboxScan(MessageId messageId, CassandraId cassandraId) {
        return Mono.fromRunnable(() ->
            LOGGER.warn("Message {} in mailbox {} do not have a corresponding time serie slice lookup entries. This " +
              "indicates underlying unexpected inconsistencies.", cassandraId.asUuid(), messageId.serialize()))
            .then(timeSerieDAO.statMailbox(cassandraId)
                .filter(m -> m.getMetadata().getMessageId().equals(messageId))
                .map(Pop3TimeSerieDAO.TimeSerieStatMetadata::getInstant)
                .map(Slice::of)
                .next());
    }

    @Override
    public Publisher<Void> clear(MailboxId mailboxId) {
        CassandraId cassandraId = (CassandraId) mailboxId;

        return timeSerieDAO.clear(cassandraId)
            .then(timeSerieLookupDAO.clear(cassandraId))
            .then(sliceMailboxDAO.clear(cassandraId));
    }
}
