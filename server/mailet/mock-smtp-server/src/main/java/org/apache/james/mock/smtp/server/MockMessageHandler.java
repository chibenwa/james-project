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

package org.apache.james.mock.smtp.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.mail.internet.AddressException;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.model.MockSMTPBehavior;
import org.apache.james.mock.smtp.server.model.MockSMTPBehaviorInformation;
import org.apache.james.mock.smtp.server.model.Response;
import org.apache.james.mock.smtp.server.model.Response.SMTPStatusCode;
import org.apache.james.mock.smtp.server.model.SMTPCommand;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;

public class MockMessageHandler implements MessageHandler {

    @FunctionalInterface
    interface Behavior<T> {
        void behave(T input) throws RejectException;
    }

    static class MockBehavior<T> implements Behavior<T> {
        private final MockSMTPBehavior behavior;

        MockBehavior(MockSMTPBehavior behavior) {
            this.behavior = behavior;
        }

        @Override
        public void behave(T input) throws RejectException {
            Response response = behavior.getResponse();
            throw new RejectException(response.getCode().getRawCode(), response.getMessage());
        }
    }

    static class BehaviorDecorator<T> implements Behavior<T> {
        private final Behavior<T> behavior;
        private final Runnable decorator;

        BehaviorDecorator(Behavior<T> behavior, Runnable decorator) {
            this.behavior = behavior;
            this.decorator = decorator;
        }

        @Override
        public void behave(T input) throws RejectException {
            try {
                behavior.behave(input);
            } finally {
                decorator.run();
            }
        }
    }

    private final Mail.Envelope.Builder envelopeBuilder;
    private final Mail.Builder mailBuilder;
    private final ReceivedMailRepository mailRepository;
    private final SMTPBehaviorRepository behaviorRepository;

    MockMessageHandler(ReceivedMailRepository mailRepository, SMTPBehaviorRepository behaviorRepository) {
        this.mailRepository = mailRepository;
        this.behaviorRepository = behaviorRepository;
        this.envelopeBuilder = new Mail.Envelope.Builder();
        this.mailBuilder = new Mail.Builder();
    }

    @Override
    public void from(String from) throws RejectException {
        Optional<Behavior<MailAddress>> fromBehavior = firstMatchedBehavior(SMTPCommand.MAIL_FROM, from);

        fromBehavior
            .orElseGet(() -> envelopeBuilder::from)
            .behave(parse(from));
    }

    @Override
    public void recipient(String recipient) throws RejectException {
        Optional<Behavior<MailAddress>> recipientBehavior = firstMatchedBehavior(SMTPCommand.RCPT_TO, recipient);

        recipientBehavior
            .orElseGet(() -> envelopeBuilder::addRecipient)
            .behave(parse(recipient));
    }

    @Override
    public void data(InputStream data) throws RejectException, TooMuchDataException, IOException {
        String dataString = readData(data);
        Optional<Behavior<String>> dataBehavior = firstMatchedBehavior(SMTPCommand.DATA, dataString);

        dataBehavior
            .orElseGet(() -> mailBuilder::message)
            .behave(dataString);
    }

    private <T> Optional<Behavior<T>> firstMatchedBehavior(SMTPCommand data, String dataLine) {
        return behaviorRepository.remainingBehaviors()
            .map(MockSMTPBehaviorInformation::getBehavior)
            .filter(behavior -> behavior.getCommand().equals(data))
            .filter(behavior -> behavior.getCondition().matches(dataLine))
            .findFirst()
            .map(behavior -> new BehaviorDecorator<>(new MockBehavior<>(behavior),
                () -> behaviorRepository.decreaseRemainingAnswers(behavior)));
    }

    @Override
    public void done() {
        Mail mail = mailBuilder.envelope(envelopeBuilder.build())
            .build();
        mailRepository.store(mail);
    }

    private String readData(InputStream data) {
        try {
            return IOUtils.toString(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RejectException(SMTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS_501.getRawCode(), "invalid data supplied");
        }
    }

    private MailAddress parse(String mailAddress) {
        try {
            return new MailAddress(mailAddress);
        } catch (AddressException e) {
            throw new RejectException(SMTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS_501.getRawCode(), "invalid email address supplied");
        }
    }
}
