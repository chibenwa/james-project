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
package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang.NotImplementedException;
import org.junit.Test;

public class GetVacationRequestTest {

    @Test
    public void getVacationRequestShouldWork() {
        GetVacationRequest getVacationRequest = GetVacationRequest.builder()
            .build();

        assertThat(getVacationRequest.getAccountId()).isNull();
    }

    @Test(expected = NotImplementedException.class)
    public void accountIdHandlingIsNotImplementedYet() {
        GetVacationRequest.builder()
            .accountId("any")
            .build();
    }

}
