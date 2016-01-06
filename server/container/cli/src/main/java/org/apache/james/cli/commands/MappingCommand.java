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

@Parameters(commandDescription = "Mappings can be used to rewrite mail addresses upon reception")
public class MappingCommand implements JamesCommand {

    private static final String ADD = "add";
    private static final String REMOVE = "remove";
    private static final String LIST = "list";

    @Parameter(names = {"-t", "--type"},
        description = "Action type. Possible values : add, remove and list",
        required = true)
    private String type;

    @Parameter(names = {"-u", "--user"},
        description = "Specifies the targetted user. Parameters : <user> <domain>",
        arity = 2)
    private List<String> userAndDomain = null;

    @Parameter(names = {"-a", "--address"}, description = "Specify an address mapping. Parameters : <fromaddress>")
    private String addresses = null;

    @Parameter(names = {"-r", "--regex"}, description = "Specify a regex mapping. Parameters : <regex>")
    private String regex = null;

    @Override
    public void validate() throws JamesCliException {
        if (type.equals(ADD) || type.equals(REMOVE)) {
            if (CommandUtils.verifyExactlyOneTrue(addresses != null, regex != null)) {
                throw new JamesCliException("Using type add or remove you should specify --address or --regex");
            }
            if (userAndDomain == null) {
                throw new JamesCliException("Using type add or remove you should specify a user.");
            }
        } else {
            if (addresses != null || regex != null) {
                throw new JamesCliException("Using type list you should not specify --address or --regex");
            }
        }
    }

    @Override
    public void execute(ServerProbe serverProbe) throws Exception {
        if (type.equals(ADD)) {
            if (addresses != null) {
                serverProbe.addAddressMapping(userAndDomain.get(0), userAndDomain.get(1), addresses);
            } else {
                serverProbe.addRegexMapping(userAndDomain.get(0), userAndDomain.get(1), regex);
            }
        }
        if (type.equals(REMOVE)) {
            if (addresses != null) {
                serverProbe.removeAddressMapping(userAndDomain.get(0), userAndDomain.get(1), addresses);
            } else {
                serverProbe.removeRegexMapping(userAndDomain.get(0), userAndDomain.get(1), regex);
            }
        }
        if (type.equals(LIST)) {
            if (userAndDomain != null) {
                serverProbe.listUserDomainMappings(userAndDomain.get(0), userAndDomain.get(1));
            } else {
                serverProbe.listMappings();
            }
        }
    }
}
