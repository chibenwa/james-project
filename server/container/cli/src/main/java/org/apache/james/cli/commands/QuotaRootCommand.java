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

@Parameters(commandDescription = "Retrieve the quotaroot identifier attached to a mailbox.")
public class QuotaRootCommand implements JamesCommand {

    @Parameter(description = "<namespace> <user> <name>",
        arity = 3)
    private List<String> mailbox;

    public void validate() throws JamesCliException {

    }

    @Override
    public void execute(ServerProbe serverProbe) throws Exception {
        System.out.println("Quota Root : " + serverProbe.getQuotaRoot(mailbox.get(0), mailbox.get(1), mailbox.get(2)));
    }
}
