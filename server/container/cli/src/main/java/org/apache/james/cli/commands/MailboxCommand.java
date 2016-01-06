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

package org.apache.james.cli.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.apache.james.cli.exceptions.JamesCliException;
import org.apache.james.cli.probe.ServerProbe;

import java.util.List;

@Parameters(commandDescription = "Allow you to do basic operations on mailboxes")
public class MailboxCommand implements JamesCommand {

    @Parameter(arity = 3,
        description = "<namespace> <user> <name>")
    private List<String> mailbox = null;

    @Parameter(names = {"-d", "--delete"},
        description = "Deletes the given mailbox")
    private boolean deletededMailbox = false;

    @Parameter(names = {"-c", "--create"},
        description = "Creates the given mailbox")
    private boolean createdMailbox = false;

    @Parameter(names = {"-r", "--reindex"},
        description = "Reindex the given mailbox")
    private boolean reindexedMailbox = false;

    public void validate() throws JamesCliException {
        if (CommandUtils.verifyExactlyOneTrue(deletededMailbox, createdMailbox, reindexedMailbox)) {
            throw new JamesCliException("You should only specify one of these options : -d -c or -r");
        }
        if (mailbox.size() != 3) {
            throw new JamesCliException("Expecting mailbox name : <namespace> <user> <mailbox>");
        }
    }

    @Override
    public void execute(ServerProbe serverProbe) throws Exception {
        if (deletededMailbox) {
            serverProbe.deleteMailbox(mailbox.get(0), mailbox.get(1), mailbox.get(2));
        }
        if (createdMailbox) {
            serverProbe.createMailbox(mailbox.get(0), mailbox.get(1), mailbox.get(2));
        }
        if (reindexedMailbox) {
            serverProbe.reIndexMailbox(mailbox.get(0), mailbox.get(1), mailbox.get(2));
        }
    }
}
