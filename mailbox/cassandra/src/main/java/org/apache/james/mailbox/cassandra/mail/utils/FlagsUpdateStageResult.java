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

package org.apache.james.mailbox.cassandra.mail.utils;

import java.util.List;
import java.util.Objects;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.UpdatedFlags;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class FlagsUpdateStageResult {
    public static FlagsUpdateStageResult success(UpdatedFlags updatedFlags) {
        return new FlagsUpdateStageResult(ImmutableList.of(), ImmutableList.of(updatedFlags));
    }

    public static FlagsUpdateStageResult fail(MessageUid uid) {
        return new FlagsUpdateStageResult(ImmutableList.of(uid), ImmutableList.of());
    }

    public static FlagsUpdateStageResult none() {
        return new FlagsUpdateStageResult(ImmutableList.of(), ImmutableList.of());
    }

    private final List<MessageUid> failed;
    private final List<UpdatedFlags> succeeded;

    @VisibleForTesting
    FlagsUpdateStageResult(List<MessageUid> failed, List<UpdatedFlags> succeeded) {
        this.failed = failed;
        this.succeeded = succeeded;
    }

    public List<MessageUid> getFailed() {
        return failed;
    }

    public List<UpdatedFlags> getSucceeded() {
        return succeeded;
    }

    public FlagsUpdateStageResult merge(FlagsUpdateStageResult other) {
        return new FlagsUpdateStageResult(
            ImmutableList.<MessageUid>builder()
                .addAll(this.failed)
                .addAll(other.failed)
                .build(),
            ImmutableList.<UpdatedFlags>builder()
                .addAll(this.succeeded)
                .addAll(other.succeeded)
                .build());
    }

    public FlagsUpdateStageResult keepSuccess() {
        return new FlagsUpdateStageResult(ImmutableList.of(), succeeded);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof FlagsUpdateStageResult) {
            FlagsUpdateStageResult that = (FlagsUpdateStageResult) o;

            return Objects.equals(this.succeeded, that.succeeded)
                && Objects.equals(this.failed, that.failed);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(failed, succeeded);
    }
}
