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

package org.apache.james.smtpserver.futurerelease;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.mailet.AttributeValue;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class FutureReleaseParameters {
    public static final String HOLDFOR_PARAMETER = "HOLDFOR";
    public static final String HOLDUNTIL_PARAMETER = "HOLDUNTIL";
    public static final long MAX_HOLD_FOR_SUPPORTED = 86400;

    public static class HoldFor {
        public static Optional<HoldFor> fromSMTPArgLine(Map<String, Long> mailFromArgLine) {
            return Optional.ofNullable(mailFromArgLine.get(HOLDFOR_PARAMETER))
                .map(HoldFor::of);
        }

        public static HoldFor fromAttributeValue(AttributeValue<Long> attributeValue) {
            return of(attributeValue.value());
        }

        private final long value;

        public static HoldFor of(long value) {
            Preconditions.checkNotNull(value);
            return new HoldFor(value);
        }

        private HoldFor(long value) {
            this.value = value;
        }

        public long value() {
            return value;
        }

        public AttributeValue<Long> toAttributeValue() {
            return AttributeValue.of(value);
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof HoldFor) {
                HoldFor holdFor = (HoldFor) o;
                return Objects.equals(this.value, holdFor.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("value", value)
                .toString();
        }
    }

    public static class HoldUntil {
        public static Optional<HoldUntil> fromSMTPArgLine(Map<String, String> mailFromArgLine) {
            return Optional.ofNullable(mailFromArgLine.get(HOLDUNTIL_PARAMETER))
                .map(HoldUntil::of);
        }

        public static HoldUntil fromAttributeValue(AttributeValue<String> attributeValue) {
            return of(attributeValue.value());
        }

        private final String value;

        private HoldUntil(String value) {
            this.value = value;
        }

        public static HoldUntil of(String value) {
            Preconditions.checkNotNull(value);
            return new HoldUntil(value);
        }

        public String asString() {
            return value;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof HoldUntil) {
                HoldUntil that = (HoldUntil) o;
                return Objects.equals(this.value, that.value);
            }
            return false;
        }

        public AttributeValue<String> toAttributeValue() {
            return AttributeValue.of(value);
        }
    }
}
