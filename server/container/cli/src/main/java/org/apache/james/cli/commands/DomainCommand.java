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
import com.google.common.base.Strings;
import org.apache.james.cli.exceptions.JamesCliException;
import org.apache.james.cli.probe.ServerProbe;

import java.io.OutputStream;
import java.util.List;

@Parameters(commandDescription = "Domain names are registered in James to play a special role in mil delivery and user " +
                                 "management when virtual hosting is enabled.")
public class DomainCommand implements JamesCommand {

    @Parameter(names = {"-a", "--add"}, description = "Add a domain. You need to specify a domain name.")
    private String createdDomain = null;

    @Parameter(names = {"-d", "--delete"}, description = "Delete a domain. You need to specify a domain name.")
    private String deletedDomain = null;

    @Parameter(names = {"-l", "--list"}, description = "List all the available domains.")
    private boolean isList = false;

    @Parameter(names = {"-c", "--contains"}, description = "Tels if the domain name specified is handled by James. You need to specify a domain name.")
    private String containsDomain = null;

    public void validate() throws JamesCliException {
        if (CommandUtils.verifyExactlyOneTrue(createdDomain != null, deletedDomain != null, containsDomain != null, isList)) {
            System.out.println(createdDomain);
            System.out.println(deletedDomain);
            System.out.println(containsDomain);
            System.out.println(isList);
            throw new JamesCliException("You should specify only one of these options : -a -d -l or -c");
        }
    }

    @Override
    public void execute(ServerProbe serverProbe) throws Exception {
        if (createdDomain != null) {
            serverProbe.addDomain(createdDomain);
        }
        if (deletedDomain != null) {
            serverProbe.removeDomain(deletedDomain);
        }
        if (containsDomain != null) {
            if (serverProbe.containsDomain(containsDomain)) {
                System.out.println(containsDomain + " exists");
            } else {
                System.out.println(containsDomain + " does not exists");
            }
        }
        if (isList) {
            CommandUtils.print(serverProbe.listDomains(), System.out);
        }
    }
}
