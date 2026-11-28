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

package org.apache.james.jmap.routes

import java.time.Duration
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import org.apache.james.events.Registration
import org.apache.james.jmap.core.OutboundMessage
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import reactor.core.publisher.{Mono, Sinks}

class ClientContextTest {
  class CountingRegistration extends Registration {
    val unregistrations: AtomicInteger = new AtomicInteger()

    override def unregister(): Mono[Void] = Mono.fromRunnable(() => unregistrations.incrementAndGet())
  }

  private def newContext(): ClientContext =
    ClientContext(Sinks.many().unicast().onBackpressureBuffer[OutboundMessage](), new AtomicReference[Registration](), null)

  @Test
  def cleanShouldUnregisterTheCurrentRegistration(): Unit = {
    val context = newContext()
    val registration = new CountingRegistration

    context.withRegistration(registration)
    context.clean()

    Awaitility.await().atMost(Duration.ofSeconds(10)).until(() => registration.unregistrations.get() == 1)
  }

  @Test
  def registrationCompletingAfterCleanShouldBeUnregistered(): Unit = {
    val context = newContext()
    val registration = new CountingRegistration

    context.clean()
    context.withRegistration(registration)

    Awaitility.await().atMost(Duration.ofSeconds(10)).until(() => registration.unregistrations.get() == 1)
    assertThat(context.pushRegistration.get()).isNull()
  }

  @Test
  def registrationAfterPushDisableShouldBeKept(): Unit = {
    val context = newContext()
    val registration = new CountingRegistration

    context.disablePush()
    context.withRegistration(registration)

    assertThat(context.pushRegistration.get()).isSameAs(registration)
    assertThat(registration.unregistrations.get()).isZero()
  }
}
