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
import org.apache.commons.io.FileUtils;
import org.apache.james.adapter.mailbox.SerializableQuota;
import org.apache.james.cli.exceptions.JamesCliException;
import org.apache.james.cli.probe.ServerProbe;
import org.apache.james.cli.utils.ValueWithUnit;
import org.apache.james.mailbox.model.Quota;

@Parameters(commandDescription = "Users are allowed to use the different protocols and to store mails in James.")
public class QuotaCommand implements JamesCommand {

    public static final String MESSAGE_COUNT = "message-count";
    public static final String STORAGE = "storage";

    @Parameter(names = {"-q", "--quotaroot"}, description = "Specifies the targeted quotaroot")
    private String quotaRoot = null;

    @Parameter(names = {"-d", "--default"}, description = "Targets the default values for quota")
    private boolean isDefault = false;

    @Parameter(names = {"-m", "--max"},
        description = "This option tells whether your command targets the current value of the quota or the maximum allowed value.")
    private boolean isMax = false;

    @Parameter(names = {"-g", "--get"}, description = "Action : Get the quota. ")
    private boolean isGet = false;

    @Parameter(names = {"-s", "--set"},
        description = "Action : Set the quota. The argument for this option is the numeric value of the max quota. You can use units K, M, G")
    private String settedValue = null;

    @Parameter(names = {"-t", "--type"},
        description = "Specify the type of the quota. It can be either message-count or storage.")
    private String type;

    public void validate() throws JamesCliException {
        if (CommandUtils.verifyExactlyOneTrue(quotaRoot != null, isDefault)) {
            throw new JamesCliException("You should specify exactly one of these options : -q or -d");
        }
        if (CommandUtils.verifyExactlyOneTrue(isGet, settedValue != null)) {
            throw new JamesCliException("You should specify only one of these options : -s or -g");
        }
        if (settedValue != null && type == null) {
            throw new JamesCliException("Type must be specified when setting quota value");
        }
        if (type != null && !type.equals(MESSAGE_COUNT) && !type.equals(STORAGE)) {
            throw new JamesCliException("Unknown type : " + type + ". Type should be either " + MESSAGE_COUNT + " or " + STORAGE);
        }
    }

    @Override
    public void execute(ServerProbe serverProbe) throws Exception {
        if (isGet && isDefault) {
            System.out.println("Default Maximum message count Quota : " + formatMessageValue(serverProbe.getDefaultMaxMessageCount()));
            System.out.println("Default Maximum Storage Quota : " + formatStorageValue(serverProbe.getDefaultMaxStorage()));
        }
        if (isGet && isMax && !isDefault) {
            System.out.println("Message count allowed for Quota Root " + quotaRoot
                + " : " + formatMessageValue(serverProbe.getMaxMessageCount(quotaRoot)));
            System.out.println("Storage space allowed for Quota Root " + quotaRoot
                + " : " + formatStorageValue(serverProbe.getMaxStorage(quotaRoot)));
        }
        if (isGet && !isMax && !isDefault) {
            printMessageQuota(quotaRoot, serverProbe.getMessageCountQuota(quotaRoot));
            printStorageQuota(quotaRoot, serverProbe.getStorageQuota(quotaRoot));
        }
        if (settedValue != null && type.equals(STORAGE) && isDefault) {
            serverProbe.setDefaultMaxStorage(ValueWithUnit.parse(settedValue).getConvertedValue());
        }
        if (settedValue != null && type.equals(MESSAGE_COUNT) && isDefault) {
            serverProbe.setDefaultMaxMessageCount(Long.parseLong(settedValue));
        }
        if (settedValue != null && type.equals(STORAGE) && !isDefault) {
            serverProbe.setMaxStorage(quotaRoot, ValueWithUnit.parse(settedValue).getConvertedValue());
        }
        if (settedValue != null && type.equals(MESSAGE_COUNT) && !isDefault) {
            serverProbe.setMaxMessageCount(quotaRoot, Long.parseLong(settedValue));
        }
    }

    private void printStorageQuota(String quotaRootString, SerializableQuota quota) {
        System.out.println(String.format("Storage quota for %s is : %s / %s",
            quotaRootString,
            formatStorageValue(quota.getUsed()),
            formatStorageValue(quota.getMax())));
    }

    private void printMessageQuota(String quotaRootString, SerializableQuota quota) {
        System.out.println(String.format("Message count quota for %s is : %s / %s",
            quotaRootString,
            formatMessageValue(quota.getUsed()),
            formatMessageValue(quota.getMax())));
    }

    private String formatStorageValue(long value) {
        if (value == Quota.UNKNOWN) {
            return ValueWithUnit.UNKNOWN;
        }
        if (value == Quota.UNLIMITED) {
            return ValueWithUnit.UNLIMITED;
        }
        return FileUtils.byteCountToDisplaySize(value);
    }

    private String formatMessageValue(long value) {
        if (value == Quota.UNKNOWN) {
            return ValueWithUnit.UNKNOWN;
        }
        if (value == Quota.UNLIMITED) {
            return ValueWithUnit.UNLIMITED;
        }
        return String.valueOf(value);
    }
}
