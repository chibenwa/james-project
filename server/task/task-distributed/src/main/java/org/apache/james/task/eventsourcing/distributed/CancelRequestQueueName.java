package org.apache.james.task.eventsourcing.distributed;

import java.util.UUID;

public class CancelRequestQueueName {
    public static CancelRequestQueueName generate() {
        return new CancelRequestQueueName(CANCEL_REQUESTS_QUEUE_NAME_PREFIX + UUID.randomUUID().toString());
    }

    private static final String CANCEL_REQUESTS_QUEUE_NAME_PREFIX = "taskManagerCancelRequestsQueue";

    private final String name;

    public CancelRequestQueueName(String name) {
        this.name = name;
    }

    public String asString() {
        return name;
    }
}
