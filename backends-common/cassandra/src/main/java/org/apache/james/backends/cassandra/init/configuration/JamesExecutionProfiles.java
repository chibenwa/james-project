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

package org.apache.james.backends.cassandra.init.configuration;

import java.util.Optional;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;

public interface JamesExecutionProfiles {
    static DriverExecutionProfile getLWTProfile(CqlSession session) {
        DriverExecutionProfile executionProfile = session.getContext().getConfig().getProfiles().get("LWT");
        return Optional.ofNullable(executionProfile)
            .orElseGet(() -> defaultLWTProfile(session));
    }

    private static DriverExecutionProfile defaultLWTProfile(CqlSession session) {
        return session.getContext().getConfig().getDefaultProfile()
            .withString(DefaultDriverOption.REQUEST_CONSISTENCY, DefaultConsistencyLevel.SERIAL.name());
    }

    static DriverExecutionProfile getCachingProfile(CqlSession session) {
        DriverExecutionProfile executionProfile = session.getContext().getConfig().getProfiles().get("CACHING");
        return Optional.ofNullable(executionProfile)
            .orElseGet(() -> defaultCachingProfile(session));
    }

    private static DriverExecutionProfile defaultCachingProfile(CqlSession session) {
        return session.getContext().getConfig().getDefaultProfile()
            .withString(DefaultDriverOption.REQUEST_CONSISTENCY, DefaultConsistencyLevel.ONE.name());
    }

    static DriverExecutionProfile getBatchProfile(CqlSession session) {
        DriverExecutionProfile executionProfile = session.getContext().getConfig().getProfiles().get("BATCH");
        return Optional.ofNullable(executionProfile)
            .orElseGet(() -> defaultBatchProfile(session));
    }

    private static DriverExecutionProfile defaultBatchProfile(CqlSession session) {
        return session.getContext().getConfig().getDefaultProfile()
            .withLong(DefaultDriverOption.REQUEST_TIMEOUT, 3600000);
    }
}
