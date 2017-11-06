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
package org.apache.james.mailbox.hbase;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.model.ReservedMailboxMatcher;
import org.apache.james.mailbox.store.MailboxManagerOptions;
import org.junit.After;
import org.junit.Ignore;

@Ignore("https://issues.apache.org/jira/browse/MAILBOX-293")
public class HBaseMailboxManagerTest extends MailboxManagerTest {

    private static final HBaseClusterSingleton CLUSTER = HBaseClusterSingleton.build();

    @Override
    protected MailboxManager provideMailboxManager(ReservedMailboxMatcher reservedMailboxMatcher) {
        return new HBaseMailboxManagerProvider().provideMailboxManager(CLUSTER,
            MailboxManagerOptions.builder()
                .withReservedMailboxMatcher(reservedMailboxMatcher)
                .build());
    }

    @After
    public void tearDown() {
        new HBaseMailboxManagerProvider().clean(CLUSTER);
    }
}
