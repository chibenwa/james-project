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

import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOXES;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOXES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_META_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_BODY_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_HEADERS_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTIONS;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTIONS_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTION_CF;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.hbase.mail.HBaseModSeqProvider;
import org.apache.james.mailbox.hbase.mail.HBaseUidProvider;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxManagerOptions;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;

import com.google.common.base.Throwables;

public class HBaseMailboxManagerProvider {
    public MailboxManager provideMailboxManager(HBaseClusterSingleton cluster, MailboxManagerOptions options) {
        ensureTables(cluster);

        HBaseUidProvider uidProvider = new HBaseUidProvider(cluster.getConf());
        HBaseModSeqProvider modSeqProvider = new HBaseModSeqProvider(cluster.getConf());
        MessageId.Factory messageIdFactory = new DefaultMessageId.Factory();
        HBaseMailboxSessionMapperFactory mapperFactory = new HBaseMailboxSessionMapperFactory(cluster.getConf(),
            uidProvider, modSeqProvider, messageIdFactory);
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, new UnionMailboxACLResolver(), options.getGroupMembershipResolver());

        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingListener);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager,
            options.getLimitAnnotationCount(),
            options.getLimitAnnotationSize());
        HBaseMailboxManager manager = new HBaseMailboxManager(mapperFactory,
            options.getAuthenticator(),
            options.getAuthorizator(),
            new JVMMailboxPathLocker(),
            options.getMessageParser(),
            messageIdFactory,
            mailboxEventDispatcher,
            delegatingListener,
            annotationManager,
            storeRightManager,
            options.getReservedMailboxMatcher());

        try {
            manager.init();
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }

        return manager;
    }

    public void clean(HBaseClusterSingleton cluster) {
        cluster.clearTable(MAILBOXES);
        cluster.clearTable(MESSAGES);
        cluster.clearTable(SUBSCRIPTIONS);
    }

    private void ensureTables(HBaseClusterSingleton cluster) {
        try {
            cluster.ensureTable(MAILBOXES_TABLE, new byte[][]{MAILBOX_CF});
            cluster.ensureTable(MESSAGES_TABLE,
                new byte[][]{MESSAGES_META_CF, MESSAGE_DATA_HEADERS_CF, MESSAGE_DATA_BODY_CF});
            cluster.ensureTable(SUBSCRIPTIONS_TABLE, new byte[][]{SUBSCRIPTION_CF});
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
