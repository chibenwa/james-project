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
package org.apache.james.mailetcontainer.impl;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor.MailetProcessorListener;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.util.MDCBuilder;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.MailetPipelineLogging;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * Mailet wrapper which execute a Mailet in a Processor
 */
public class ProcessorImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorImpl.class);

    private final MetricFactory metricFactory;
    private final Mailet mailet;
    private final MailetProcessorImpl processor;

    public ProcessorImpl(MetricFactory metricFactory, MailetProcessorImpl processor, Mailet mailet) {
        this.metricFactory = metricFactory;
        this.processor = processor;
        this.mailet = mailet;
    }

    /**
     * Call the wrapped mailet for the exchange
     */
    public Publisher<Void> process(Mail mail) {
        long start = System.currentTimeMillis();
        TimeMetric timeMetric = metricFactory.timer(mailet.getClass().getSimpleName());
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "MAILET")
                     .addToContext(MDCBuilder.ACTION, "MAILET")
                     .addToContext(MDCBuilder.HOST, mail.getRemoteHost())
                     .addToContext("state", mail.getState())
                     .addToContext("mailet", mailet.getClass().getSimpleName())
                     .addToContext("mail", mail.getName())
                     .addToContext("recipients", ImmutableList.copyOf(mail.getRecipients()).toString())
                     .addToContext("sender", mail.getMaybeSender().asString())
                     .build()) {
            MailetPipelineLogging.logBeginOfMailetProcess(mailet, mail);
            return Mono.from(mailet.serviceReactive(mail))
                .onErrorResume(Throwing.function(ex -> {
                    String onMailetException = null;

                    MailetConfig mailetConfig = mailet.getMailetConfig();
                    if (mailetConfig instanceof MailetConfigImpl) {
                        onMailetException = mailetConfig.getInitParameter("onMailetException");
                    }
                    if (onMailetException == null) {
                        onMailetException = Mail.ERROR;
                    } else {
                        onMailetException = onMailetException.trim().toLowerCase(Locale.US);
                    }
                    if (onMailetException.equalsIgnoreCase("ignore")) {
                        // ignore the exception and continue
                        // this option should not be used if the mail object can be
                        // changed by the mailet
                        LOGGER.warn("Encountered error while executing mailet {}. Ignoring it.", mailet, ex);
                        ProcessorUtil.verifyMailAddresses(mail.getRecipients());
                    } else if (onMailetException.equalsIgnoreCase("propagate")) {
                        return Mono.error(ex);
                    } else {
                        ProcessorUtil.handleException(ex, mail, mailet.getMailetConfig().getMailetName(), onMailetException, LOGGER);
                    }
                    return Mono.fromRunnable(() -> {
                        timeMetric.stopAndPublish();
                        MailetPipelineLogging.logEndOfMailetProcess(mailet, mail);
                        List<MailetProcessorListener> listeners = processor.getListeners();
                        long complete = System.currentTimeMillis() - start;
                        if (mail.getRecipients().isEmpty()) {
                            mail.setState(Mail.GHOST);
                        }
                        for (MailetProcessorListener listener : listeners) {
                            listener.afterMailet(mailet, mail.getName(), mail.getState(), complete, ex);
                        }
                    });
                }))
                .doOnSuccess(aVoid -> {
                    timeMetric.stopAndPublish();
                    MailetPipelineLogging.logEndOfMailetProcess(mailet, mail);
                    List<MailetProcessorListener> listeners = processor.getListeners();
                    long complete = System.currentTimeMillis() - start;
                    if (mail.getRecipients().isEmpty()) {
                        mail.setState(Mail.GHOST);
                    }
                    for (MailetProcessorListener listener : listeners) {
                        listener.afterMailet(mailet, mail.getName(), mail.getState(), complete, null);
                    }
                });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
