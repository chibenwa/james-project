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

import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetVacationRequest;
import org.apache.james.jmap.model.SetVacationResponse;
import org.apache.james.jmap.model.VacationResponse;
import org.apache.james.mailbox.MailboxSession;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class SetVacationResponseMethod implements Method {

    public static final Request.Name METHOD_NAME = Request.name("setVacationResponse");
    public static final Response.Name RESPONSE_NAME = Response.name("vacationResponseSet");
    public static final String INVALID_ARGUMENTS = "invalidArguments";
    public static final String ERROR_MESSAGE_BASE = "There is one VacationResponse object per account, with id set to \"singleton\" and not to ";
    public static final String INVALID_ARGUMENTS1 = "invalidArguments";
    public static final String INVALID_ARGUMENT_DESCRIPTION = "update field should just contain one entry with key \"singleton\"";

    private final VacationRepository vacationRepository;

    @Inject
    public SetVacationResponseMethod(VacationRepository vacationRepository) {
        this.vacationRepository = vacationRepository;
    }

    @Override
    public Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return SetVacationRequest.class;
    }

    @Override
    public Stream<JmapResponse> process(JmapRequest request, ClientId clientId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(clientId);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof SetVacationRequest);
        SetVacationRequest setVacationRequest = (SetVacationRequest) request;

        if (!validateRequestStructure(setVacationRequest)) {
            return Stream.of(JmapResponse
                .builder()
                .clientId(clientId)
                .error(ErrorResponse.builder()
                    .type(INVALID_ARGUMENTS1)
                    .description(INVALID_ARGUMENT_DESCRIPTION)
                    .build())
                .build());
        }

        return process(clientId,
            AccountId.create(mailboxSession.getUser().getUserName()),
            setVacationRequest.getUpdate().get(Vacation.ID));
    }

    private boolean validateRequestStructure(SetVacationRequest setVacationRequest) {
        return setVacationRequest.getUpdate().entrySet().size() == 1 && setVacationRequest.getUpdate().containsKey(Vacation.ID);
    }

    private Stream<JmapResponse> process(ClientId clientId, AccountId accountId, VacationResponse vacationResponse) {
        boolean isValid = isValid(vacationResponse);

        if (isValid) {
            vacationRepository.modifyVacation(accountId, convertToVacation(vacationResponse));
        }

        return Stream.of(JmapResponse.builder()
            .clientId(clientId)
            .responseName(RESPONSE_NAME)
            .response(generateSetVacationResponse(isValid, vacationResponse))
            .build());
    }

    private boolean isValid(VacationResponse vacationResponse) {
        return vacationResponse.getId().equals(Vacation.ID);
    }

    public SetVacationResponse generateSetVacationResponse(boolean isValid, VacationResponse vacationResponses) {
        if (isValid) {
            return SetVacationResponse.builder()
                .updatedId(Vacation.ID)
                .build();
        } else {
            return SetVacationResponse.builder()
                .notUpdated(ImmutableMap.of(Vacation.ID,
                    SetError.builder()
                        .type(INVALID_ARGUMENTS)
                        .description(ERROR_MESSAGE_BASE + vacationResponses.getId())
                        .build()))
                .build();
        }
    }

    public Vacation convertToVacation(VacationResponse vacationResponse) {
        return Vacation.builder()
            .enabled(vacationResponse.isEnabled())
            .fromDate(vacationResponse.getFromDate())
            .toDate(vacationResponse.getToDate())
            .textBody(vacationResponse.getTextBody())
            .build();
    }

}
