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

package org.apache.james.cli;

import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.james.adapter.mailbox.SerializableQuota;
import org.apache.james.cli.probe.ServerProbe;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

public class ServerCmdTest {

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    private IMocksControl control;

    private ServerProbe serverProbe;

    private ServerCmd testee;

    @Before
    public void setup() {
        control = createControl();
        serverProbe = control.createMock(ServerProbe.class);
        testee = new ServerCmd(serverProbe);
    }

    @Test
    public void addDomainCommandShouldWork() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "domain", "--add", domain};

        serverProbe.addDomain(domain);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void removeDomainCommandShouldWork() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "domain", "--delete", domain};

        serverProbe.removeDomain(domain);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void containsDomainCommandShouldWork() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "domain", "--contains", domain};

        expect(serverProbe.containsDomain(domain)).andReturn(true);

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void listDomainsCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "domain", "--list"};

        String[] res = {};
        expect(serverProbe.listDomains()).andReturn(res);

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void addUserCommandShouldWork() throws Exception {
        String user = "user@domain";
        String password = "password";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "user", "--add", user, password};

        serverProbe.addUser(user, password);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void removeUserCommandShouldWork() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "user", "--delete", user};

        serverProbe.removeUser(user);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void listUsersCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "user", "--list"};

        String[] res = {};
        expect(serverProbe.listUsers()).andReturn(res);

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void listMappingsCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mapping", "--type", "list"};

        expect(serverProbe.listMappings()).andReturn(new HashMap<String, Mappings>());

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void listUserDomainMappingsCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mapping", "--type", "list", "--user", user, domain};

        expect(serverProbe.listUserDomainMappings(user, domain)).andReturn(MappingsImpl.empty());

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void addAddressCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String address = "bis@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mapping", "--type", "add", "--user", user, domain, "--address", address};

        serverProbe.addAddressMapping(user, domain, address);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void removeAddressCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String address = "bis@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mapping", "--type", "remove", "--user", user, domain, "--address", address};

        serverProbe.removeAddressMapping(user, domain, address);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void addRegexMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String regex = "bis.*@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mapping", "--type", "add", "--user", user, domain, "--regex", regex};

        serverProbe.addRegexMapping(user, domain, regex);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void removeRegexMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String regex = "bis.*@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mapping", "--type", "remove", "--user", user, domain, "--regex", regex};

        serverProbe.removeRegexMapping(user, domain, regex);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void setPasswordCommandShouldWork() throws Exception {
        String user = "user@domain";
        String password = "pass";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "user", "--set-password", user, password};

        serverProbe.setPassword(user, password);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void copyMailboxesCommandShouldWork() throws Exception {
        String srcBean = "srcBean";
        String dstBean = "dstBean";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mailboxes", "--copy", srcBean, dstBean};

        serverProbe.copyMailbox(srcBean, dstBean);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void deleteUserMailboxesCommandShouldWork() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mailboxes", "--delete", user};

        serverProbe.deleteUserMailboxesNames(user);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void createMailboxCommandShouldWork() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mailbox", "--create", namespace, user, name};

        serverProbe.createMailbox(namespace, user, name);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void deleteMailboxCommandShouldWork() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mailbox", "--delete", namespace, user, name};

        serverProbe.deleteMailbox(namespace, user, name);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void listUserMailboxesCommandShouldWork() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "user", "-m", user};

        expect(serverProbe.listUserMailboxes(user)).andReturn(new ArrayList<String>());

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void getQuotaRootCommandShouldWork() throws Exception {
        String namespace = "#private";
        String user = "user@domain";
        String name = "INBOX";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "quotaroot", namespace, user, name};

        expect(serverProbe.getQuotaRoot(namespace, user, name)).andReturn(namespace + "&" + user);

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void getDefaultMaxQuotasCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "quota", "--get", "--default"};

        expect(serverProbe.getDefaultMaxStorage()).andReturn(1024L * 1024L * 1024L);
        expect(serverProbe.getDefaultMaxMessageCount()).andReturn(1024L * 1024L);

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void setDefaultMaxMessageCountCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "quota",  "--type", "message-count", "--set", "1054", "--default"};

        serverProbe.setDefaultMaxMessageCount(1054);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void setDefaultMaxStorageCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "quota",  "--type", "storage", "--set", "1G", "--default"};

        serverProbe.setDefaultMaxStorage(1024 * 1024 * 1024);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void setMaxMessageCountCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "quota",  "--type", "message-count", "--set", "1000", "--quotaroot", quotaroot};

        serverProbe.setMaxMessageCount(quotaroot, 1000);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void setMaxStorageCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "quota",  "--type", "storage", "--set", "5M", "--quotaroot", quotaroot};

        serverProbe.setMaxStorage(quotaroot, 5 * 1024 * 1024);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void getMaxMessageCountCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "quota", "--get", "--max", "--quotaroot", quotaroot};

        expect(serverProbe.getMaxMessageCount(quotaroot)).andReturn(Quota.UNLIMITED);
        expect(serverProbe.getMaxStorage(quotaroot)).andReturn(1024L);

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void getMessageCountCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "quota", "--get",  "--quotaroot", quotaroot};

        expect(serverProbe.getMessageCountQuota(quotaroot)).andReturn(new SerializableQuota(25, Quota.UNLIMITED));
        expect(serverProbe.getStorageQuota(quotaroot)).andReturn(new SerializableQuota(Quota.UNKNOWN,1024L));

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void reIndexAllQuotaCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mailboxes", "--reindex"};

        serverProbe.reIndexAll();
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void reIndexMailboxCommandShouldWork() throws Exception {
        String namespace = "#private";
        String user = "btellier@apache.org";
        String name = "INBOX";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mailbox", "--reindex", namespace, user, name};

        serverProbe.reIndexMailbox(namespace, user, name);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void addDomainCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "domain", "-a"};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void removeDomainCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "domain", "-d"};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void containsDomainCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "domain", "-c"};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void addUserCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "user", "-a", user};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void removeUserCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "user", "-d"};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void listUserDomainMappingsCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mapping", "--type", "list", "--user", user};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void addAddressCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mapping", "--type", "add", "--user", user, "--address"};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void removeAddressCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mapping", "--type", "remove", "--user", user, "--address"};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void addRegexMappingCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mapping", "--type", "add", "--user", user, "--regex"};


        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void removeRegexMappingCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mapping", "--type", "remove", "--user", user, "--regex"};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void setPasswordMappingCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "user", "--set-password", user};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void copyMailboxMappingCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String srcBean = "srcBean";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mailboxes", "--copy", srcBean};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void deleteUserMailboxesMappingCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mailboxes", "--delete"};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void createMailboxMappingCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String user = "user@domain";
        String namespace = "#private";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mailbox", "--create", namespace, user};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void deleteMailboxMappingCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String user = "user@domain";
        String namespace = "#private";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "mailbox", "--delete", namespace, user};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void listUserMailboxesMappingsCommandShouldExitOnMissingArguments() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "user", "--list-mailboxes"};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void executeCommandLineShouldExitOnUnrecognizedCommands() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "wrongCommand"};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void executeCommandLineShouldThrowWhenOnlyOptionAreProvided() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999"};

        control.replay();
        try {
            testee.executeCommandLine(arguments);
        } finally {
            control.verify();
        }
    }

    @Test
    public void executeCommandLinehouldThrowWhenInvalidOptionIsProvided() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-v", "-h", "127.0.0.1", "-p", "9999", "mailboxes", "--reindex"};

        serverProbe.reIndexAll();
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void executeCommandLineShouldExitOnNullPortValueOption() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-h", "127.0.0.1", "-p", "0", "mailboxes", "--reindex"};

        serverProbe.reIndexAll();
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void executeCommandLineShouldExitOnNegativePortValueOption() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-h", "127.0.0.1", "-p", "-1", "mailboxes", "--reindex"};

        serverProbe.reIndexAll();
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void executeCommandLineShouldExitOnTooHighPortValueOption() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String[] arguments = { "-h", "127.0.0.1", "-p", "999999", "mailboxes", "--reindex"};

        serverProbe.reIndexAll();
        expectLastCall();

        control.replay();
        testee.executeCommandLine(arguments);
        control.verify();
    }

    @Test
    public void executeCommandLineShouldPrintHelp() throws Exception {
        String[] arguments = { "--help"};

        testee.executeCommandLine(arguments);
    }
}
