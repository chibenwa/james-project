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

import java.util.Set;

import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;

import com.google.common.collect.ImmutableSet;

public class FutureReleaseEHLOHook implements HeloHook {
    private static final long MAX_FUTURE_RELEASE_INTERVAL = 86400;
    private static final String MAX_FUTURE_RELEASE_DATE_TIME = "2023-04-14T10:00:00Z";

    @Override
    public Set<String> implementedEsmtpFeatures() {
        return ImmutableSet.of("FUTURERELEASE " + MAX_FUTURE_RELEASE_INTERVAL + " " + MAX_FUTURE_RELEASE_DATE_TIME);
    }

    @Override
    public HookResult doHelo(SMTPSession session, String helo) {
        return HookResult.DECLINED;
    }
}

