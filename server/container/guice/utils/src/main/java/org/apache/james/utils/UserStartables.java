package org.apache.james.utils;

import javax.inject.Inject;

import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;

public class UserStartables implements Startable {
    public static class Module extends AbstractModule {
        @ProvidesIntoSet
        @Singleton
        InitializationOperation graphiteExporter(UserStartables startables) {
            return InitilizationOperationBuilder
                .forClass(UserStartables.class)
                .init(startables::start);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(UserStartables.class);

    private final GuiceGenericLoader loader;
    private final ExtensionConfiguration extensionConfiguration;

    @Inject
    public UserStartables(GuiceGenericLoader loader, ExtensionConfiguration extensionConfiguration) {
        this.loader = loader;
        this.extensionConfiguration = extensionConfiguration;
    }

    public void start() {
        extensionConfiguration.getStartables()
            .stream()
            .map(Throwing.<ClassName, UserDefinedStartable>function(loader::instantiate))
            .peek(module -> LOGGER.info("Starting " + module.getClass().getCanonicalName()))
            .forEach(UserDefinedStartable::start);
    }
}
