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

package org.apache.james.pop3server.task;

import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesDTO;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesService;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesTask;
import org.junit.jupiter.api.Test;

class MetaDataFixInconsistenciesDTOTest {
    private static final String JSON = "{" +
        "  \"type\":\"Pop3MetaDataFixInconsistenciesTask\"," +
        "  \"runningOptions\":{\"messagesPerSecond\":36}" +
        "}";

    @Test
    public void shouldMatchSerializableContract() throws Exception {
        MetaDataFixInconsistenciesService service = mock(MetaDataFixInconsistenciesService.class);
        JsonSerializationVerifier.dtoModule(MetaDataFixInconsistenciesDTO.module(service))
            .bean(new MetaDataFixInconsistenciesTask(service, MetaDataFixInconsistenciesService.RunningOptions.withMessageRatePerSecond(36)))
            .json(JSON)
            .verify();
    }
}