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

import com.google.common.base.Joiner;

import java.io.PrintStream;
import java.util.Arrays;

public class CommandUtils {

    public static boolean verifyExactlyOneTrue(boolean... conditions) {
        int count = 0;
        for(boolean condition : conditions) {
            if (condition) {
                count++;
            }
        }
        return count != 1;
    }

    public static void print(String[] data, PrintStream out) {
        print(Arrays.asList(data), out);
    }

    public static void print(Iterable<String> data, PrintStream out) {
        if (data != null) {
            for (String u : data) {
                out.println(u);
            }
            out.println(Joiner.on('\n').join(data));
        }
    }

}
