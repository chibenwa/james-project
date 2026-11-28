/** **************************************************************
  * Licensed to the Apache Software Foundation (ASF) under one   *
  * or more contributor license agreements.  See the NOTICE file *
  * distributed with this work for additional information        *
  * regarding copyright ownership.  The ASF licenses this file   *
  * to you under the Apache License, Version 2.0 (the            *
  * "License"); you may not use this file except in compliance   *
  * with the License.  You may obtain a copy of the License at   *
  * *
  * http://www.apache.org/licenses/LICENSE-2.0                 *
  * *
  * Unless required by applicable law or agreed to in writing,   *
  * software distributed under the License is distributed on an  *
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
  * KIND, either express or implied.  See the License for the    *
  * specific language governing permissions and limitations      *
  * under the License.                                           *
  * ***************************************************************/
package org.apache.james.task.eventsourcing

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import org.apache.james.eventsourcing.ReactiveSubscriber
import org.apache.james.task.{Hostname, TaskExecutionDetails, TaskId, TaskManager}
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.util.retry.Retry

import scala.compat.java8.OptionConverters._
import scala.jdk.CollectionConverters._

object TaskExecutionDetailsProjection {
  val MAX_CONCURRENT_UPDATE_RETRIES: Int = 10

  class ConcurrentUpdateException(taskId: TaskId) extends RuntimeException(s"Task execution details of ${taskId.asString()} were concurrently updated")
}

trait TaskExecutionDetailsProjection {
  import TaskExecutionDetailsProjection._

  def asSubscriber(hostname: Hostname): ReactiveSubscriber = event => event.event match {
    case created: Created =>
      createReactive(TaskExecutionDetails.from(created.task, created.aggregateId.taskId, created.hostname))
    case cancelRequested: CancelRequested =>
      updateReactive(cancelRequested.aggregateId.taskId)(_.cancelRequested(hostname))
    case started: Started =>
      updateReactive(started.aggregateId.taskId)(_.started(hostname))
    case completed: Completed =>
      updateReactive(completed.aggregateId.taskId)(_.completed(completed.additionalInformation.asJava))
    case failed: Failed =>
      updateReactive(failed.aggregateId.taskId)(_.failed(failed.additionalInformation.asJava))
    case canceled: Cancelled =>
      updateReactive(canceled.aggregateId.taskId)(_.cancelEffectively(canceled.additionalInformation.asJava))
    case updated: AdditionalInformationUpdated =>
      updateReactive(updated.aggregateId.taskId)(_.updateInformation(updated.additionalInformation))
  }

  // Load, update then save only if the status did not change meanwhile, otherwise retry on fresh details:
  // a blind save of stale details would revert a concurrent status transition (eg COMPLETED back to IN_PROGRESS)
  private def updateReactive(taskId: TaskId)(updater: TaskExecutionDetails => TaskExecutionDetails): Publisher[Void] =
    Mono.defer(() => Mono.from(loadReactive(taskId)))
      .flatMap(current => saveIfStatusUnchanged(current, updater(current)))
      .retryWhen(Retry.max(MAX_CONCURRENT_UPDATE_RETRIES)
        .filter(_.isInstanceOf[ConcurrentUpdateException]))
      .`then`()

  private def saveIfStatusUnchanged(current: TaskExecutionDetails, updated: TaskExecutionDetails): Mono[Void] =
    if (updated eq current) {
      Mono.empty()
    } else {
      Mono.from(updateReactiveIfStatus(current.getStatus, updated))
        .flatMap(applied => if (applied) Mono.empty[Void]() else Mono.error[Void](new ConcurrentUpdateException(current.getTaskId)))
    }

  def load(taskId: TaskId): Option[TaskExecutionDetails]
  def list: List[TaskExecutionDetails]
  def update(details: TaskExecutionDetails): Unit

  def loadReactive(taskId: TaskId): Publisher[TaskExecutionDetails]

  def listReactive(): Publisher[TaskExecutionDetails]

  def listDetailsByBeforeDate(beforeDate: Instant): Publisher[TaskExecutionDetails]

  def updateReactive(details: TaskExecutionDetails): Publisher[Void]

  def createReactive(details: TaskExecutionDetails): Publisher[Void] = updateReactive(details)

  /**
   * Atomically saves the details only if the stored status of the task is the expected one.
   *
   * @return true if the details were saved, false if the task is absent or its status changed
   */
  def updateReactiveIfStatus(expectedStatus: TaskManager.Status, details: TaskExecutionDetails): Publisher[java.lang.Boolean]

  def remove(taskExecutionDetails: TaskExecutionDetails) : Publisher[Void]
}

class MemoryTaskExecutionDetailsProjection() extends TaskExecutionDetailsProjection {
  private[this] val details = new ConcurrentHashMap[TaskId, TaskExecutionDetails]

  override def load(taskId: TaskId): Option[TaskExecutionDetails] = Option(this.details.get(taskId))

  override def list: List[TaskExecutionDetails] = this.details.values().asScala.toList

  override def update(details: TaskExecutionDetails): Unit = this.details.put(details.taskId, details)


  override def loadReactive(taskId: TaskId): Publisher[TaskExecutionDetails] = SMono.fromCallable(() => this.details.get(taskId))

  override def listReactive(): Publisher[TaskExecutionDetails] = SFlux.fromIterable(this.details.values().asScala)

  override def updateReactive(details: TaskExecutionDetails): Publisher[Void] = SMono.fromCallable(() => this.details.put(details.taskId, details)).`then`(SMono.empty)

  override def updateReactiveIfStatus(expectedStatus: TaskManager.Status, updatedDetails: TaskExecutionDetails): Publisher[java.lang.Boolean] =
    SMono.fromCallable(() => {
      val applied = new AtomicBoolean(false)
      this.details.computeIfPresent(updatedDetails.taskId, (_, current) => {
        val hasExpectedStatus = current.getStatus == expectedStatus
        applied.set(hasExpectedStatus)
        if (hasExpectedStatus) updatedDetails else current
      })
      java.lang.Boolean.valueOf(applied.get())
    })

  override def listDetailsByBeforeDate(beforeDate: Instant): Publisher[TaskExecutionDetails] = SFlux.fromIterable(this.details.values().asScala)
    .filter(taskExecutionDetails => taskExecutionDetails.getSubmittedDate.toInstant.isBefore(beforeDate))

  override def remove(taskExecutionDetails: TaskExecutionDetails): Publisher[Void] =
    SMono.fromCallable(() => details.remove(taskExecutionDetails.taskId)).`then`(SMono.empty)

}
