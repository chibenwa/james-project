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

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.common.base.Strings;

public class JMXCommand {

    public static class PortValidator implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            long n = Long.parseLong(value);
            if (n < 1 || n > 65535) {
                throw new ParameterException("Port number should be between 1 and 65535");
            }
        }
    }

    @Parameter(names = {"-h", "--host"}, description = "James host")
    private String host = "127.0.0.1";

    @Parameter(names = {"-p", "--port"},
        description = "James JMX port",
        validateWith = PortValidator.class)
    private int port = 9999;

    @Parameter(names = {"--help"}, description = "View help")
    private boolean help;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isHelp() {
        return help;
    }

}
