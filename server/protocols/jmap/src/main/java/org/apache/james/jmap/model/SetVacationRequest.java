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

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.methods.JmapRequest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.collect.ImmutableMap;

@JsonDeserialize(builder = SetVacationRequest.Builder.class)
public class SetVacationRequest implements JmapRequest {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private ImmutableMap<String, VacationResponse> update;

        public Builder accountId(String accountId) {
            throw new NotImplementedException();
        }

        public Builder update(Map<String, VacationResponse> update) {
            this.update = ImmutableMap.copyOf(update);
            return this;
        }

        public SetVacationRequest build() {
            return new SetVacationRequest(Optional.ofNullable(update).orElse(ImmutableMap.of()));
        }
    }

    private final Map<String, VacationResponse> update;

    private SetVacationRequest(Map<String, VacationResponse> update) {
        this.update = update;
    }

    public Map<String, VacationResponse> getUpdate() {
        return update;
    }
}
