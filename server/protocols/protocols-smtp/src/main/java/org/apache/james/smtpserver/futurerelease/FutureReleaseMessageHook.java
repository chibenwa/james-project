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

package org.apache.james.smtpserver.futurerelease;

import static org.apache.james.smtpserver.futurerelease.FutureReleaseMailParameterHook.FUTURERELEASE_HOLDFOR;

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.smtpserver.JamesMessageHook;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;

public class FutureReleaseMessageHook implements JamesMessageHook {
    public static final AttributeName MAIL_ATTRIBUTE_HOLD_FOR = AttributeName.of("futurerelease-hold-for");
    @Override
    public HookResult onMessage(SMTPSession session, Mail mail) {
        long holdFor = session.getAttachment(FUTURERELEASE_HOLDFOR, ProtocolSession.State.Transaction).map(FutureReleaseParameters.HoldFor::value).orElse(0L);
        mail.setAttribute(new Attribute(MAIL_ATTRIBUTE_HOLD_FOR, AttributeValue.of(holdFor)));
        return HookResult.DECLINED;
    }
}
