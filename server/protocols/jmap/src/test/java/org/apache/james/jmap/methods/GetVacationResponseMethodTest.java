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

package org.apache.james.jmap.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMailboxesRequest;
import org.apache.james.jmap.model.GetVacationRequest;
import org.apache.james.jmap.model.GetVacationResponse;
import org.apache.james.jmap.model.SetMailboxesRequest;
import org.apache.james.jmap.model.VacationResponse;
import org.apache.james.mailbox.MailboxSession;
import org.junit.Before;
import org.junit.Test;

public class GetVacationResponseMethodTest {

    public static final String USERNAME = "username";
    private GetVacationResponseMethod testee;
    private VacationRepository vacationRepository;
    private MailboxSession mailboxSession;

    @Before
    public void setUp() {
        vacationRepository = mock(VacationRepository.class);
        mailboxSession = mock(MailboxSession.class);
        testee = new GetVacationResponseMethod(vacationRepository);
    }

    @Test(expected = NullPointerException.class)
    public void processShouldThrowOnNullRequest() {
        testee.process(null, mock(ClientId.class), mock(MailboxSession.class));
    }

    @Test(expected = NullPointerException.class)
    public void processShouldThrowOnNullClientId() {
        testee.process(mock(GetMailboxesRequest.class), null, mock(MailboxSession.class));
    }

    @Test(expected = NullPointerException.class)
    public void processShouldThrowOnNullMailboxSession() {
        testee.process(mock(GetMailboxesRequest.class), mock(ClientId.class), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void processShouldThrowOnWrongRequestType() {
        testee.process(mock(SetMailboxesRequest.class), mock(ClientId.class), mock(MailboxSession.class));
    }

    @Test
    public void processShouldWork() {
        ClientId clientId = mock(ClientId.class);
        Vacation vacation = Vacation.builder()
            .enabled(true)
            .textBody("I am in vacation")
            .build();
        when(vacationRepository.retrieveVacation(AccountId.create(USERNAME))).thenReturn(vacation);
        when(mailboxSession.getUser()).thenReturn(new MailboxSession.User() {
            @Override
            public String getUserName() {
                return USERNAME;
            }

            @Override
            public String getPassword() {
                return null;
            }

            @Override
            public List<Locale> getLocalePreferences() {
                return null;
            }
        });

        GetVacationRequest getVacationRequest = GetVacationRequest.builder().build();

        Stream<JmapResponse> result = testee.process(getVacationRequest, clientId, mailboxSession);

        JmapResponse expected = JmapResponse.builder()
            .clientId(clientId)
            .responseName(GetVacationResponseMethod.RESPONSE_NAME)
            .response(GetVacationResponse.builder()
                .accountId(USERNAME)
                .setVacationResponse(VacationResponse.builder()
                    .fromVacation(vacation)
                    .build())
                .build())
            .build();
        assertThat(result).containsExactly(expected);
    }

}
