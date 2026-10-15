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

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManagerV2;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.store.mail.MailboxCountersStore;
import org.apache.james.utils.GuiceProbe;

import reactor.core.publisher.Mono;

public class RedisMailboxCachesProbe implements GuiceProbe {
    final CurrentQuotaManager currentQuotaManager;
    final MailboxCountersStore mailboxCountersStore;
    private final CassandraCurrentQuotaManagerV2 cassandraCurrentQuotaManager;
    private final CassandraMailboxCounterDAO cassandraMailboxCounterDAO;
    private final UserQuotaRootResolver quotaRootResolver;
    private final MailboxManager mailboxManager;

    @Inject
    public RedisMailboxCachesProbe(CurrentQuotaManager currentQuotaManager, MailboxCountersStore mailboxCountersStore,
                                   CassandraCurrentQuotaManagerV2 cassandraCurrentQuotaManager, CassandraMailboxCounterDAO cassandraMailboxCounterDAO,
                                   UserQuotaRootResolver quotaRootResolver, MailboxManager mailboxManager) {
        this.currentQuotaManager = currentQuotaManager;
        this.mailboxCountersStore = mailboxCountersStore;
        this.cassandraCurrentQuotaManager = cassandraCurrentQuotaManager;
        this.cassandraMailboxCounterDAO = cassandraMailboxCounterDAO;
        this.quotaRootResolver = quotaRootResolver;
        this.mailboxManager = mailboxManager;
    }

    QuotaCountUsage currentMessageCount(Username username) {
        return Mono.from(currentQuotaManager.getCurrentMessageCount(quotaRootResolver.forUser(username))).block();
    }

    CurrentQuotas cassandraCurrentQuotas(Username username) {
        return cassandraCurrentQuotaManager.getCurrentQuotas(quotaRootResolver.forUser(username)).block();
    }

    MailboxCounters mailboxCounters(MailboxPath mailboxPath) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(mailboxPath.getUser());
        return mailboxManager.getMailbox(mailboxPath, session).getMailboxCounters(session);
    }

    MailboxCounters cassandraMailboxCounters(MailboxPath mailboxPath) throws MailboxException {
        Mailbox mailbox = mailbox(mailboxPath);
        return cassandraMailboxCounterDAO.retrieveMailboxCounters(mailbox)
            .defaultIfEmpty(MailboxCounters.empty(mailbox.getMailboxId()))
            .block();
    }

    void addToCassandraMailboxCounters(MailboxCounters counters) {
        cassandraMailboxCounterDAO.add(counters).block();
    }

    Mailbox mailbox(MailboxPath mailboxPath) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(mailboxPath.getUser());
        return mailboxManager.getMailbox(mailboxPath, session).getMailboxEntity();
    }
}
