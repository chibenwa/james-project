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
package org.apache.james.metrics.logger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.james.metrics.api.TimeMetric;

import com.google.common.base.Stopwatch;

public class DefaultTimeMetric implements TimeMetric {
    static class DefaultExecutionResult implements ExecutionResult {
        private final Duration elasped;

        DefaultExecutionResult(Duration elasped) {
            this.elasped = elasped;
        }

        @Override
        public Duration elasped() {
            return elasped;
        }

        @Override
        public boolean exceedP99() {
            return false;
        }

        @Override
        public boolean exceedP50() {
            return false;
        }

        @Override
        public ExecutionResult logWhenExceedP99(Duration thresholdInNanoSeconds) {
            return this;
        }
    }

    private final String name;
    private final Stopwatch stopwatch;

    public DefaultTimeMetric(String name) {
        this.name = name;
        this.stopwatch = Stopwatch.createStarted();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ExecutionResult stopAndPublish() {
        long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        DefaultMetricFactory.LOGGER.info("Time spent in {}: {} ms.", name, elapsed);
        return new DefaultExecutionResult(Duration.ofNanos(elapsed));
    }

}
