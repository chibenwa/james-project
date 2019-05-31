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

package org.apache.james;

import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.data.CassandraDLPConfigurationStoreModule;
import org.apache.james.modules.data.CassandraDomainListModule;
import org.apache.james.modules.data.CassandraJmapModule;
import org.apache.james.modules.data.CassandraMailRepositoryModule;
import org.apache.james.modules.data.CassandraRecipientRewriteTableModule;
import org.apache.james.modules.data.CassandraSieveRepositoryModule;
import org.apache.james.modules.data.CassandraUsersRepositoryModule;
import org.apache.james.modules.eventstore.CassandraEventStoreModule;
import org.apache.james.modules.mailbox.BlobStoreAPIModule;
import org.apache.james.modules.mailbox.CassandraMailboxModule;
import org.apache.james.modules.mailbox.CassandraObjectStoreModule;
import org.apache.james.modules.mailbox.CassandraQuotaMailingModule;
import org.apache.james.modules.mailbox.CassandraSessionModule;
import org.apache.james.modules.mailbox.ElasticSearchClientModule;
import org.apache.james.modules.mailbox.ElasticSearchMailboxModule;
import org.apache.james.modules.mailbox.TikaMailboxModule;
import org.apache.james.modules.metrics.CassandraMetricsModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.JMAPServerModule;
import org.apache.james.modules.protocols.LMTPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.POP3ServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.server.CassandraDataRoutesModules;
import org.apache.james.modules.server.CassandraRoutesModule;
import org.apache.james.modules.server.DLPRoutesModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.ElasticSearchMetricReporterModule;
import org.apache.james.modules.server.MailQueueRoutesModule;
import org.apache.james.modules.server.MailRepositoriesRoutesModule;
import org.apache.james.modules.server.MailboxRoutesModule;
import org.apache.james.modules.server.MessageIdReIndexingModule;
import org.apache.james.modules.server.ReIndexingModule;
import org.apache.james.modules.server.SieveRoutesModule;
import org.apache.james.modules.server.SwaggerRoutesModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.modules.spamassassin.SpamAssassinListenerModule;
import org.apache.james.modules.vault.DeletedMessageVaultModule;
import org.apache.james.modules.vault.DeletedMessageVaultRoutesModule;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class CassandraModules {
    public static final Module WEBADMIN = Modules.combine(
        new CassandraDataRoutesModules(),
        new CassandraRoutesModule(),
        new DataRoutesModules(),
        new DeletedMessageVaultRoutesModule(),
        new DLPRoutesModule(),
        new MailboxRoutesModule(),
        new MailQueueRoutesModule(),
        new MailRepositoriesRoutesModule(),
        new MessageIdReIndexingModule(),
        new ReIndexingModule(),
        new SieveRoutesModule(),
        new SwaggerRoutesModule(),
        new WebAdminServerModule());

    public static final Module PROTOCOLS = Modules.combine(
        new CassandraJmapModule(),
        new IMAPServerModule(),
        new JMAPServerModule(),
        new LMTPServerModule(),
        new ManageSieveServerModule(),
        new POP3ServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule(),
        WEBADMIN);

    public static final Module PLUGINS = Modules.combine(
        new CassandraQuotaMailingModule());

    public static final Module BLOB_MODULE = Modules.combine(
        new BlobExportMechanismModule(),
        new BlobStoreAPIModule());

    public static final Module CASSANDRA_SERVER_CORE_MODULE = Modules.combine(
        new CassandraDLPConfigurationStoreModule(),
        new CassandraDomainListModule(),
        new CassandraEventStoreModule(),
        new CassandraMailRepositoryModule(),
        new CassandraMetricsModule(),
        new CassandraObjectStoreModule(),
        new CassandraRecipientRewriteTableModule(),
        new CassandraSessionModule(),
        new CassandraSieveRepositoryModule(),
        new CassandraUsersRepositoryModule(),
        BLOB_MODULE);

    public static final Module CASSANDRA_MAILBOX_MODULE = Modules.combine(
        new CassandraMailboxModule(),
        new DeletedMessageVaultModule(),
        new ElasticSearchClientModule(),
        new ElasticSearchMailboxModule(),
        new ElasticSearchMetricReporterModule(),
        new MailboxModule(),
        new SpamAssassinListenerModule(),
        new TikaMailboxModule());

    public static Module ALL_BUT_JMX_CASSANDRA_MODULE = Modules.combine(
        CASSANDRA_SERVER_CORE_MODULE,
        CASSANDRA_MAILBOX_MODULE,
        PROTOCOLS,
        PLUGINS);
}
