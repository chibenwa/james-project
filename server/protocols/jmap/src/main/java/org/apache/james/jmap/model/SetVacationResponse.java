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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.jmap.methods.Method;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetVacationResponse implements Method.Response {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String updatedId;
        private Map<String, SetError> notUpdated;

        public Builder updatedId(String updatedId) {
            this.updatedId = updatedId;
            return this;
        }

        public Builder notUpdated(Map<String, SetError> notUpdated) {
            this.notUpdated = notUpdated;
            return this;
        }

        public SetVacationResponse build() {
            return new SetVacationResponse(
                Optional.ofNullable(updatedId)
                    .map(ImmutableList::of)
                    .orElse(ImmutableList.of()),
                Optional.ofNullable(notUpdated)
                    .orElse(ImmutableMap.of()));
        }
    }

    private final List<String> updated;
    private final Map<String, SetError> notUpdated;

    private SetVacationResponse(List<String> updated, Map<String, SetError> notUpdated) {
        this.updated = updated;
        this.notUpdated = notUpdated;
    }

    public List<String> getUpdated() {
        return updated;
    }

    public Map<String, SetError> getNotUpdated() {
        return notUpdated;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SetVacationResponse that = (SetVacationResponse) o;

        return Objects.equals(this.updated, that.updated)
            && Objects.equals(this.notUpdated, that.notUpdated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(updated, notUpdated);
    }
}
