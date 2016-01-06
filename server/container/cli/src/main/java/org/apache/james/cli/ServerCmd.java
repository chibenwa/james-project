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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import org.apache.commons.lang.time.StopWatch;
import org.apache.james.cli.commands.CommandUtils;
import org.apache.james.cli.commands.DomainCommand;
import org.apache.james.cli.commands.JMXCommand;
import org.apache.james.cli.commands.JamesCommand;
import org.apache.james.cli.commands.MailboxCommand;
import org.apache.james.cli.commands.MailboxesCommand;
import org.apache.james.cli.commands.MappingCommand;
import org.apache.james.cli.commands.QuotaCommand;
import org.apache.james.cli.commands.QuotaRootCommand;
import org.apache.james.cli.commands.UserCommand;
import org.apache.james.cli.exceptions.JamesCliException;
import org.apache.james.cli.probe.ServerProbe;
import org.apache.james.cli.probe.impl.JmxServerProbe;

/**
 * Command line utility for managing various aspect of the James server.
 */
public class ServerCmd {

    /**
     * Main method to initialize the class.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        new ServerCmd().executeCommandLine(args);
        stopWatch.split();
        CommandUtils.print(new String[] { Joiner.on(' ')
                .join("Command executed in", stopWatch.getSplitTime(), "ms.")},
            System.out);
        stopWatch.stop();
        System.exit(0);
    }

    private final Map<String, JamesCommand> commandMap;
    private final JCommander jCommander;
    @VisibleForTesting
    final JMXCommand jmxCommand;
    private final ServerProbe defaultServerProbe;

    public ServerCmd(ServerProbe defaultServerProbe) {
        commandMap = computeCommandMap();
        jmxCommand = new JMXCommand();
        jCommander = instantiateJCommander();
        this.defaultServerProbe = defaultServerProbe;
    }

    public ServerCmd() {
        this(null);
    }

    public JCommander instantiateJCommander() {
        JCommander jCommander = new JCommander(jmxCommand);
        for (Entry<String, JamesCommand> entry : commandMap.entrySet()) {
            jCommander.addCommand(entry.getKey(), entry.getValue());
        }
        return jCommander;
    }

    public Map<String, JamesCommand> computeCommandMap() {
        Map<String, JamesCommand> commandMap;
        commandMap = new HashMap<String, JamesCommand>();
        commandMap.put("domain", new DomainCommand());
        commandMap.put("mailbox", new MailboxCommand());
        commandMap.put("mailboxes", new MailboxesCommand());
        commandMap.put("mapping", new MappingCommand());
        commandMap.put("quota", new QuotaCommand());
        commandMap.put("quotaroot", new QuotaRootCommand());
        commandMap.put("user", new UserCommand());
        return commandMap;
    }

    private void failWithMessage(String s) {
        System.err.println(s);
     //   jCommander.usage();
        System.exit(1);
    }

    @VisibleForTesting
    void executeCommandLine(String[] args) {
        try {
            jCommander.parse(args);
            if (jmxCommand.isHelp()) {
                jCommander.usage();
            } else {
                JamesCommand executedCommand = commandMap.get(jCommander.getParsedCommand());
                if (executedCommand != null) {
                    executedCommand.validate();
                    executedCommand.execute(retrieveServerProbe());
                } else {
                    failWithMessage("You must specify a command");
                }
            }
        } catch (JamesCliException e) {
            failWithMessage(e.getMessage());
        } catch (ParameterException e) {
            failWithMessage("Error parsing command line : " + e.getMessage());
        } catch (IOException ioe) {
            failWithMessage("Error connecting to remote JMX agent : " + ioe.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            failWithMessage("Error while executing command : " + e.getMessage());
        }
    }

    private ServerProbe retrieveServerProbe() throws IOException {
        ServerProbe serverProbe;
        if (defaultServerProbe == null) {
            serverProbe = new JmxServerProbe(jmxCommand.getHost(), jmxCommand.getPort());
        } else {
            serverProbe = defaultServerProbe;
        }
        return serverProbe;
    }

}
