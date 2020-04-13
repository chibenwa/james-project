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
package org.apache.james.mailbox.cassandra;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

public class CassandraMailboxManagerTest extends MailboxManagerTest<CassandraMailboxManager> {
    private static final ImmutableList<MailboxListener.GroupMailboxListener> NO_ADDITIONAL_LISTENERS = ImmutableList.of();

    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(MailboxAggregateModule.MODULE_WITH_QUOTA);

    @Override
    protected CassandraMailboxManager provideMailboxManager() {
        return CassandraMailboxManagerProvider.provideMailboxManager(
            cassandra.getCassandraCluster(),
            new PreDeletionHooks(preDeletionHooks(), new RecordingMetricFactory()),
            NO_ADDITIONAL_LISTENERS);
    }

    @Override
    protected CassandraMailboxManager provideMailboxManager(MailboxListener.GroupMailboxListener listener) {
        return CassandraMailboxManagerProvider.provideMailboxManager(
            cassandra.getCassandraCluster(),
            new PreDeletionHooks(preDeletionHooks(), new RecordingMetricFactory()),
            ImmutableList.of(listener));
    }

    @Override
    protected EventBus retrieveEventBus(CassandraMailboxManager mailboxManager) {
        return mailboxManager.getEventBus().get();
    }
}
