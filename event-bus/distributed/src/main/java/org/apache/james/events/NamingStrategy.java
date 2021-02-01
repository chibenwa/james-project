package org.apache.james.events;

import reactor.rabbitmq.QueueSpecification;

public class NamingStrategy {
    private final String prefix;

    public NamingStrategy(String prefix) {
        this.prefix = prefix;
    }

    public RegistrationQueueName queueName(EventBusId eventBusId) {
        return  new RegistrationQueueName(prefix + "-eventbus-" + eventBusId.asString());
    }

    public QueueSpecification deadLetterQueue() {
        return QueueSpecification.queue(prefix + "-dead-letter-queue");
    }

    public String exchange() {
        return prefix + "-exchange";
    }

    public String deadLetterExchange() {
        return prefix + "-dead-letter-exchange";
    }

    public GroupConsumerRetry.RetryExchangeName retryExchange(Group group) {
        return new GroupConsumerRetry.RetryExchangeName(prefix, group);
    }

    public GroupRegistration.WorkQueueName workQueue(Group group) {
        return new GroupRegistration.WorkQueueName(prefix, group);
    }
}
