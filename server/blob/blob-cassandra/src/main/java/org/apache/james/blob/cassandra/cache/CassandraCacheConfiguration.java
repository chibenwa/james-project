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

package org.apache.james.blob.cassandra.cache;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;
import org.apache.james.util.SizeFormat;

import com.google.common.base.Preconditions;

public class CassandraCacheConfiguration {


    public static class Builder {
        private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMillis(100);
        private static final Duration MAX_READ_TIMEOUT = Duration.ofHours(1);
        private static final Duration DEFAULT_TTL = Duration.ofDays(7);
        private static final int DEFAULT_BYTE_THRESHOLD_SIZE = 8 * 1024;
        public static final int DEFAULT_IN_FLIGHT_REQUESTS_THRESHOLD = 256;

        private Optional<Duration> readTimeout = Optional.empty();
        private Optional<Integer> sizeThresholdInBytes = Optional.empty();
        private Optional<Integer> inFlightRequestsThreshold = Optional.empty();
        private Optional<Duration> ttl = Optional.empty();

        public Builder timeOut(Duration timeout) {
            Preconditions.checkNotNull(timeout, "'Read timeout' must not to be null");
            Preconditions.checkArgument(timeout.toMillis() > 0, "'Read timeout' needs to be positive");
            Preconditions.checkArgument(timeout.getSeconds() <= MAX_READ_TIMEOUT.getSeconds(),
                "'Read timeout' needs to be less than %s sec", MAX_READ_TIMEOUT.getSeconds());

            this.readTimeout = Optional.of(timeout);
            return this;
        }

        public Builder sizeThresholdInBytes(int sizeThresholdInBytes) {
            Preconditions.checkArgument(sizeThresholdInBytes >= 0, "'Threshold size' needs to be positive");

            this.sizeThresholdInBytes = Optional.of(sizeThresholdInBytes);
            return this;
        }

        public Builder inFlightRequestsThreshold(int inFlightRequestsThreshold) {
            Preconditions.checkArgument(inFlightRequestsThreshold > 0, "'inFlightRequestsThreshold' needs to be strictly positive");

            this.inFlightRequestsThreshold = Optional.of(inFlightRequestsThreshold);
            return this;
        }

        public Builder inFlightRequestsThreshold(Optional<Integer> inFlightRequestsThreshold) {
            inFlightRequestsThreshold.ifPresent(this::inFlightRequestsThreshold);
            return this;
        }

        public Builder ttl(Duration ttl) {
            Preconditions.checkNotNull(ttl, "'TTL' must not to be null");
            Preconditions.checkArgument(ttl.getSeconds() > 0, "'TTL' needs to be positive");
            Preconditions.checkArgument(ttl.getSeconds() < Integer.MAX_VALUE,
                "'TTL' must not greater than %s sec", Integer.MAX_VALUE);

            this.ttl = Optional.of(ttl);
            return this;
        }

        public Builder ttl(Optional<Duration> ttl) {
            ttl.ifPresent(this::ttl);
            return this;
        }

        public Builder timeOut(Optional<Duration> timeOut) {
            timeOut.ifPresent(this::timeOut);
            return this;
        }

        public Builder sizeThresholdInBytes(Optional<Integer> sizeThresholdInBytes) {
            sizeThresholdInBytes.ifPresent(this::sizeThresholdInBytes);
            return this;
        }

        public CassandraCacheConfiguration build() {
            return new CassandraCacheConfiguration(
                readTimeout.orElse(DEFAULT_READ_TIMEOUT),
                sizeThresholdInBytes.orElse(DEFAULT_BYTE_THRESHOLD_SIZE),
                inFlightRequestsThreshold.orElse(DEFAULT_IN_FLIGHT_REQUESTS_THRESHOLD),
                ttl.orElse(DEFAULT_TTL));
        }
    }

    public static final CassandraCacheConfiguration DEFAULT = builder().build();

    public static Builder builder() {
        return new Builder();
    }

    public static CassandraCacheConfiguration from(Configuration configuration) {
        Optional<Duration> ttl = Optional.ofNullable(configuration.getString("cache.cassandra.ttl", null))
            .map(value -> DurationParser.parse(value, ChronoUnit.SECONDS));
        Optional<Duration> timeOut = Optional.ofNullable(configuration.getString("cache.cassandra.timeout", null))
            .map(value -> DurationParser.parse(value, ChronoUnit.SECONDS));
        Optional<Integer> sizeThreshold = Optional.ofNullable(configuration.getString("cache.sizeThresholdInBytes", null))
            .map(SizeFormat::parseAsByteCount)
            .map(Math::toIntExact);
        Optional<Integer> inFlightRequestsThreshold = Optional.ofNullable(configuration.getInteger("cache.inFlightRequests.threshold", null));

        return builder()
            .ttl(ttl)
            .timeOut(timeOut)
            .sizeThresholdInBytes(sizeThreshold)
            .inFlightRequestsThreshold(inFlightRequestsThreshold)
            .build();
    }

    private final Duration readTimeOut;
    private final int sizeThresholdInBytes;
    private final int inFlightRequestsThreshold;
    private final Duration ttl;

    private CassandraCacheConfiguration(Duration timeout, int sizeThresholdInBytes, int inFlightRequestsThreshold, Duration ttl) {
        this.readTimeOut = timeout;
        this.sizeThresholdInBytes = sizeThresholdInBytes;
        this.inFlightRequestsThreshold = inFlightRequestsThreshold;
        this.ttl = ttl;
    }

    public int getInFlightRequestsThreshold() {
        return inFlightRequestsThreshold;
    }

    public Duration getReadTimeOut() {
        return readTimeOut;
    }

    public Duration getTtl() {
        return ttl;
    }

    public int getSizeThresholdInBytes() {
        return sizeThresholdInBytes;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CassandraCacheConfiguration) {
            CassandraCacheConfiguration that = (CassandraCacheConfiguration) o;

            return Objects.equals(this.sizeThresholdInBytes, that.sizeThresholdInBytes)
                && Objects.equals(this.readTimeOut, that.readTimeOut)
                && Objects.equals(this.inFlightRequestsThreshold, that.inFlightRequestsThreshold)
                && Objects.equals(this.ttl, that.ttl);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(readTimeOut, inFlightRequestsThreshold, sizeThresholdInBytes, ttl);
    }
}
