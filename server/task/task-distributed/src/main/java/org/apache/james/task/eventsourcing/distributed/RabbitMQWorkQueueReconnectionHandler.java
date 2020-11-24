package org.apache.james.task.eventsourcing.distributed;

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;

import javax.inject.Inject;

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;

public class RabbitMQWorkQueueReconnectionHandler implements SimpleConnectionPool.ReconnectionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQWorkQueueReconnectionHandler.class);
    private final CancelRequestQueueName cancelRequestQueueName;

    @Inject
    public RabbitMQWorkQueueReconnectionHandler(CancelRequestQueueName cancelRequestQueueName) {
        this.cancelRequestQueueName = cancelRequestQueueName;
    }

    @Override
    public Publisher<Void> handleReconnection(Connection connection) {
        return Mono.fromRunnable(() -> {
            try (Channel channel = connection.createChannel()) {
                channel.queueDeclare(cancelRequestQueueName.asString(), !DURABLE, !EXCLUSIVE, AUTO_DELETE, ImmutableMap.of());
            } catch (Exception e) {
                LOGGER.error("Error recovering connection", e);
            }
        });
    }
}
