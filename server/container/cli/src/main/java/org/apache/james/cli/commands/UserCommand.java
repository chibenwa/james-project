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

@Parameters(commandDescription = "Users are allowed to use the different protocols and to store mails in James.")
public class UserCommand implements JamesCommand {

    @Parameter(names = {"-a", "--add"},
        description = "Action : Add a user. You need to specify a username and password.",
        arity = 2)
    private List<String> createUser = null;

    @Parameter(names = {"-d", "--delete"}, description = "Action : Delete a user. You need to specify a username.")
    private String removeUser = null;

    @Parameter(names = {"-l", "--list"}, description = "Action : List all users.")
    private boolean isList = false;

    @Parameter(names = {"-m", "--list-mailboxes"}, description = "Action : List user's mailboxes.")
    private String userToListMailboxes;

    @Parameter(names = {"-s", "--set-password"},
        description = "Action : Set the password for a user. You need to specify a username and a password.",
        arity = 2)
    private List<String> setPassword = null;

    public void validate() throws JamesCliException {
        if (CommandUtils.verifyExactlyOneTrue(createUser != null, removeUser != null, isList, setPassword != null, userToListMailboxes != null)) {
            throw new JamesCliException("You should specify only one of these options : -a -d -l -m or -s");
        }
    }

    @Override
    public void execute(ServerProbe serverProbe) throws Exception {
        if (createUser != null) {
            serverProbe.addUser(createUser.get(0), createUser.get(1));
        }
        if (removeUser != null) {
            serverProbe.removeUser(removeUser);
        }
        if (setPassword != null) {
            serverProbe.setPassword(setPassword.get(0), setPassword.get(1));
        }
        if (isList) {
            CommandUtils.print(serverProbe.listUsers(), System.out);
        }
        if (userToListMailboxes != null) {
            CommandUtils.print(serverProbe.listUserMailboxes(userToListMailboxes), System.out);
        }
    }
}
