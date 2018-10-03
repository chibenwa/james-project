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

package org.apache.james.jmap.memory;

import java.util.Random;

import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJMAPModules;
import org.apache.james.jmap.methods.integration.SetMessagesMethodContract;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MemorySetMessagesMethodTest extends SetMessagesMethodContract {
    private Random random = new Random();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = JamesServerExtension.builder()
        .server(MemoryJMAPModules.DEFAULT_MEMORY_JMAP_SERVER)
        .build();
    
    @Override
    protected void await() {
    }
    
    @Override
    protected MessageId randomMessageId() {
        return new InMemoryMessageId.Factory().fromString(String.valueOf(random.nextInt(100000) + 100));
    }

}
