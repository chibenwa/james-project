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

package org.apache.james.task.eventsourcing;

import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_2;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_UPDATED;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static scala.jdk.javaapi.CollectionConverters.asJava;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.ReactiveSubscriber;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryReferenceWithCounterTask;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskType;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import scala.Option;
import scala.compat.java8.OptionConverters;

public interface TaskExecutionDetailsProjectionContract {
    Hostname HOSTNAME = new Hostname("node-1");
    Task TASK = () -> TaskType.of("projection-contract");
    int CONCURRENT_UPDATE_ROUNDS = 50;

    TaskExecutionDetailsProjection testee();

    @Test
    default void loadShouldBeAbleToRetrieveASavedRecord() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());

        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID()));

        assertThat(taskExecutionDetails.get())
            .usingRecursiveComparison()
            .ignoringFields("submittedDate")
            .isEqualTo(TASK_EXECUTION_DETAILS());

        assertThat(taskExecutionDetails.get().getSubmittedDate().isEqual(TASK_EXECUTION_DETAILS().getSubmittedDate()))
            .isTrue();
    }

    @Test
    default void readDetailsShouldBeAbleToRetrieveASavedRecordWithAdditionalInformation() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION());

        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID()));

        assertThat(taskExecutionDetails.get())
            .usingRecursiveComparison()
            .ignoringFields("submittedDate")
            .isEqualTo(TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION());

        assertThat(taskExecutionDetails.get().getSubmittedDate().isEqual(TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION().getSubmittedDate()))
            .isTrue();
    }

    @Test
    default void updateShouldUpdateRecords() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());

        testee.update(TASK_EXECUTION_DETAILS_UPDATED());

        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID()));

        assertThat(taskExecutionDetails.get())
            .usingRecursiveComparison()
            .ignoringFields("submittedDate")
            .isEqualTo(TASK_EXECUTION_DETAILS_UPDATED());

        assertThat(taskExecutionDetails.get().getSubmittedDate().isEqual(TASK_EXECUTION_DETAILS_UPDATED().getSubmittedDate()))
            .isTrue();
    }

    @Test
    default void loadShouldReturnEmptyWhenNone() {
        TaskExecutionDetailsProjection testee = testee();
        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID()));
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    default void listShouldReturnEmptyWhenNone() {
        TaskExecutionDetailsProjection testee = testee();
        List<TaskExecutionDetails> taskExecutionDetails = asJava(testee.list());
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    default void listShouldReturnAllRecords() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());
        testee.update(TASK_EXECUTION_DETAILS_2());

        List<TaskExecutionDetails> taskExecutionDetails = asJava(testee.list());

        assertThat(taskExecutionDetails)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("submittedDate")
            .containsOnly(TASK_EXECUTION_DETAILS(), TASK_EXECUTION_DETAILS_2());
    }

    @Test
    default void listDetailsShouldReturnLastUpdatedRecords() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());
        testee.update(TASK_EXECUTION_DETAILS_UPDATED());

        List<TaskExecutionDetails> taskExecutionDetails = asJava(testee.list());
        assertThat(taskExecutionDetails)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("submittedDate")
            .containsOnly(TASK_EXECUTION_DETAILS_UPDATED());
    }

    @Test
    default void updateReactiveIfStatusShouldSaveWhenExpectedStatus() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());

        Boolean applied = Mono.from(testee.updateReactiveIfStatus(TaskManager.Status.COMPLETED, TASK_EXECUTION_DETAILS_UPDATED())).block();

        assertThat(applied).isTrue();
        assertThat(testee.load(TASK_ID()).get().getStatus()).isEqualTo(TaskManager.Status.FAILED);
    }

    @Test
    default void updateReactiveIfStatusShouldNotSaveWhenOtherStatus() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());

        Boolean applied = Mono.from(testee.updateReactiveIfStatus(TaskManager.Status.IN_PROGRESS, TASK_EXECUTION_DETAILS_UPDATED())).block();

        assertThat(applied).isFalse();
        assertThat(testee.load(TASK_ID()).get().getStatus()).isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    default void updateReactiveIfStatusShouldNotSaveWhenAbsent() {
        TaskExecutionDetailsProjection testee = testee();

        Boolean applied = Mono.from(testee.updateReactiveIfStatus(TaskManager.Status.COMPLETED, TASK_EXECUTION_DETAILS_UPDATED())).block();

        assertThat(applied).isFalse();
        assertThat(OptionConverters.toJava(testee.load(TASK_ID()))).isEmpty();
    }

    @Test
    default void completionShouldNotBeRevertedByAConcurrentAdditionalInformationUpdate() {
        TaskExecutionDetailsProjection testee = testee();
        ReactiveSubscriber subscriber = testee.asSubscriber(HOSTNAME);

        List<TaskManager.Status> statuses = IntStream.range(0, CONCURRENT_UPDATE_ROUNDS)
            .mapToObj(round -> completeWhileUpdatingAdditionalInformation(testee, subscriber))
            .toList();

        assertThat(statuses).containsOnly(TaskManager.Status.COMPLETED);
    }

    private TaskManager.Status completeWhileUpdatingAdditionalInformation(TaskExecutionDetailsProjection testee, ReactiveSubscriber subscriber) {
        TaskId taskId = TaskId.generateTaskId();
        TaskAggregateId aggregateId = new TaskAggregateId(taskId);
        handle(subscriber, new Created(aggregateId, EventId.fromSerialized(0), TASK, HOSTNAME)).block();
        handle(subscriber, new Started(aggregateId, EventId.fromSerialized(1), HOSTNAME)).block();

        Mono.when(
                handle(subscriber, new AdditionalInformationUpdated(aggregateId, EventId.fromSerialized(2),
                    new MemoryReferenceWithCounterTask.AdditionalInformation(1, Instant.EPOCH))).subscribeOn(Schedulers.parallel()),
                handle(subscriber, new Completed(aggregateId, EventId.fromSerialized(3), Task.Result.COMPLETED, Option.empty())).subscribeOn(Schedulers.parallel()))
            .block();

        return testee.load(taskId).get().getStatus();
    }

    private Mono<Void> handle(ReactiveSubscriber subscriber, Event event) {
        return Mono.from(subscriber.handleReactive(new EventWithState(event, Option.empty()))).then();
    }
}
