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

@Parameters(commandDescription = "Actions on several mailboxes")
public class MailboxesCommand implements JamesCommand {

    @Parameter(names = {"-d", "--delete"},
        description = "Deletes the mailbox belonging to the given user. Args : <user>")
    private String userToDeleteMailboxes = null;

    @Parameter(names = {"-c", "--copy"},
        arity = 2,
        description = "Copy mailboxes from one bean to an other. Args : <srcBean> <dstBean>")
    private List<String> beans = null;

    @Parameter(names = {"-r", "--reindex"},
        description = "Reindex the mailboxes")
    private boolean reindexedMailboxes = false;

    public void validate() throws JamesCliException {
        if (CommandUtils.verifyExactlyOneTrue(userToDeleteMailboxes != null, beans != null, reindexedMailboxes)) {
            throw new JamesCliException("You should only specify one of these options : -d or -b");
        }
    }

    @Override
    public void execute(ServerProbe serverProbe) throws Exception {
        if (beans != null) {
            serverProbe.copyMailbox(beans.get(0), beans.get(1));
        }
        if (userToDeleteMailboxes != null) {
            serverProbe.deleteUserMailboxesNames(userToDeleteMailboxes);
        }
        if (reindexedMailboxes) {
            serverProbe.reIndexAll();
        }
    }
}
