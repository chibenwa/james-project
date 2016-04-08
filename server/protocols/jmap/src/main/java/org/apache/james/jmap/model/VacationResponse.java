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

package org.apache.james.jmap.model;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.apache.james.jmap.api.vacation.Vacation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Preconditions;

@JsonDeserialize(builder = VacationResponse.Builder.class)
public class VacationResponse {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String id;
        private boolean isEnabled;
        private ZonedDateTime fromDate;
        private ZonedDateTime toDate;
        private String textBody;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        @JsonProperty("isEnabled")
        public Builder enabled(boolean enabled) {
            isEnabled = enabled;
            return this;
        }

        public Builder fromDate(ZonedDateTime fromDate) {
            this.fromDate = fromDate;
            return this;
        }

        public Builder toDate(ZonedDateTime toDate) {
            this.toDate = toDate;
            return this;
        }

        public Builder textBody(String textBody) {
            this.textBody = textBody;
            return this;
        }

        public Builder fromVacation(Vacation vacation) {
            this.id = vacation.getId();
            this.isEnabled = vacation.isEnabled();
            this.fromDate = vacation.getFromDate().orElse(null);
            this.toDate = vacation.getToDate().orElse(null);
            this.textBody = vacation.getTextBody();
            return this;
        }

        public VacationResponse build() {
            Preconditions.checkNotNull(textBody, "textBody property of vacationResponse object should not be null");
            return new VacationResponse(id, isEnabled, fromDate, toDate, textBody);
        }
    }

    private final String id;
    private final boolean isEnabled;
    private final ZonedDateTime fromDate;
    private final ZonedDateTime toDate;
    private final String textBody;

    private VacationResponse(String id, boolean isEnabled, ZonedDateTime fromDate, ZonedDateTime toDate, String textBody) {
        this.id = id;
        this.isEnabled = isEnabled;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.textBody = textBody;
    }

    public String getId() {
        return id;
    }

    @JsonProperty("isEnabled")
    public boolean isEnabled() {
        return isEnabled;
    }

    public ZonedDateTime getFromDate() {
        return fromDate;
    }

    public ZonedDateTime getToDate() {
        return toDate;
    }

    public String getTextBody() {
        return textBody;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VacationResponse that = (VacationResponse) o;

        return Objects.equals(this.id, that.id)
            && Objects.equals(this.isEnabled, that.isEnabled)
            && Objects.equals(this.fromDate, that.fromDate)
            && Objects.equals(this.toDate, that.toDate)
            && Objects.equals(this.textBody, that.textBody);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, isEnabled, fromDate, toDate, textBody);
    }
}
