package org.apache.james.pop3server.mailbox.tombstone;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;

import com.google.common.base.MoreObjects;

public class ClockConfiguration {
    public static final ClockConfiguration DEFAULT = new ClockConfiguration(Duration.ofMinutes(1));

    public static ClockConfiguration from(Configuration configuration) {
        return Optional.ofNullable(configuration.getString("clockSkewDefense", null))
            .map((String s) -> DurationParser.parse(s, ChronoUnit.MINUTES))
            .map(ClockConfiguration::new)
            .orElse(DEFAULT);
    }

    private final Duration clockSkewDefense;

    public ClockConfiguration(Duration clockSkewDefense) {
        this.clockSkewDefense = clockSkewDefense;
    }

    public Duration getClockSkewDefense() {
        return clockSkewDefense;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ClockConfiguration) {
            ClockConfiguration that = (ClockConfiguration) o;

            return Objects.equals(this.clockSkewDefense, that.clockSkewDefense);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(clockSkewDefense);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("clockSkewDefense", clockSkewDefense)
            .toString();
    }
}
