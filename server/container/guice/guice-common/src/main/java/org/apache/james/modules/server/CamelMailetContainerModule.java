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

package org.apache.james.modules.server;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.james.mailetcontainer.api.MatcherLoader;
import org.apache.james.mailetcontainer.api.jmx.MailSpoolerMBean;
import org.apache.james.mailetcontainer.impl.JamesMailSpooler;
import org.apache.james.mailetcontainer.impl.JamesMailetContext;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.mailetcontainer.impl.camel.CamelCompositeProcessor;
import org.apache.james.mailetcontainer.impl.camel.CamelMailetProcessor;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.ConfigurationProvider;
import org.apache.james.utils.GuiceMailetLoader;
import org.apache.james.utils.GuiceMatcherLoader;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class CamelMailetContainerModule extends AbstractModule {

    private static final Logger CAMEL_LOGGER = LoggerFactory.getLogger(CamelCompositeProcessor.class);
    private static final Logger SPOOLER_LOGGER = LoggerFactory.getLogger(JamesMailSpooler.class);
    private static final Logger MAILET_LOGGER = LoggerFactory.getLogger(JamesMailetContext.class);

    @Override
    protected void configure() {
        bind(MailProcessor.class).to(CamelCompositeProcessor.class).in(Singleton.class);
        bind(MailSpoolerMBean.class).to(JamesMailSpooler.class);
        bind(MailetLoader.class).to(GuiceMailetLoader.class);
        bind(MatcherLoader.class).to(GuiceMatcherLoader.class);
        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(MailetModuleConfigurationPerformer.class);
    }

    @Provides
    @Singleton
    private MailetContext provideMailetContext(MailQueueFactory mailQueueFactory,
                                                    DNSService dns,
                                                    UsersRepository localusers,
                                                    DomainList domains) {
        JamesMailetContext jamesMailetContext = new JamesMailetContext();
        jamesMailetContext.setLog(MAILET_LOGGER);
        jamesMailetContext.setDNSService(dns);
        jamesMailetContext.retrieveRootMailQueue(mailQueueFactory);
        jamesMailetContext.setUsersRepository(localusers);
        jamesMailetContext.setDomainList(domains);
        return jamesMailetContext;
    }

    @Singleton
    public static class MailetModuleConfigurationPerformer implements ConfigurationPerformer {

        private final ConfigurationProvider configurationProvider;
        private final CamelCompositeProcessor camelCompositeProcessor;
        private final JamesMailSpooler jamesMailSpooler;
        private final JamesMailetContext mailetContext;
        private Set<TransportProcessorCheck> transportProcessorCheckSet;

        @Inject
        public MailetModuleConfigurationPerformer(ConfigurationProvider configurationProvider,
                                                CamelCompositeProcessor camelCompositeProcessor,
                                                JamesMailSpooler jamesMailSpooler,
                                                JamesMailetContext mailetContext,
                                                Set<TransportProcessorCheck> transportProcessorCheckSet) {
            this.configurationProvider = configurationProvider;
            this.camelCompositeProcessor = camelCompositeProcessor;
            this.jamesMailSpooler = jamesMailSpooler;
            this.mailetContext = mailetContext;
            this.transportProcessorCheckSet = transportProcessorCheckSet;
        }

        @Override
        public void initModule() throws Exception {
            camelCompositeProcessor.setLog(CAMEL_LOGGER);
            camelCompositeProcessor.setCamelContext(new DefaultCamelContext());
            camelCompositeProcessor.configure(configurationProvider.getConfiguration("mailetcontainer").configurationAt("processors"));
            camelCompositeProcessor.init();
            checkProcessors();
            jamesMailSpooler.setMailProcessor(camelCompositeProcessor);
            jamesMailSpooler.setLog(SPOOLER_LOGGER);
            jamesMailSpooler.configure(configurationProvider.getConfiguration("mailetcontainer").configurationAt("spooler"));
            jamesMailSpooler.init();
            mailetContext.setLog(MAILET_LOGGER);
            mailetContext.configure(configurationProvider.getConfiguration("mailetcontainer").configurationAt("context"));
        }

        private void checkProcessors() throws ConfigurationException {
            MailProcessor mailProcessor = Optional.ofNullable(camelCompositeProcessor.getProcessor("transport"))
                .orElseThrow(() -> new RuntimeException("JMAP needs a transport processor"));
            if (mailProcessor instanceof CamelMailetProcessor) {
                List<MatcherMailetPair> matcherMailetPairs = ((CamelMailetProcessor) mailProcessor).getPairs();
                for (TransportProcessorCheck check : transportProcessorCheckSet) {
                    check.check(matcherMailetPairs);
                }
            } else {
                throw new RuntimeException("Can not perform checks as transport processor is not an instance of " + MailProcessor.class);
            }
        }
    }

    @FunctionalInterface
    public interface TransportProcessorCheck {
        void check(List<MatcherMailetPair> pairs) throws ConfigurationException;
    }

}
