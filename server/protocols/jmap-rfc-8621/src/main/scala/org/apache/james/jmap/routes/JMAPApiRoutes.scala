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
package org.apache.james.jmap.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.stream
import java.util.stream.Stream

import com.fasterxml.jackson.core.JsonParseException
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus.OK
import javax.inject.{Inject, Named}
import org.apache.http.HttpStatus.SC_BAD_REQUEST
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.JMAPUrls.JMAP
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.http.{Authenticator, MailboxesProvisioner, UserProvisioning}
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.method.Method
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.MethodName
import org.apache.james.jmap.model.ProblemDetails.{notJSONProblem, notRequestProblem, unknownCapabilityProblem}
import org.apache.james.jmap.model._
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.MailboxSession
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsError, JsSuccess}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import scala.jdk.CollectionConverters._

object JMAPApiRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[JMAPApiRoutes])
}

class JMAPApiRoutes (val authenticator: Authenticator,
                     serializer: Serializer,
                     userProvisioner: UserProvisioning,
                     mailboxesProvisioner: MailboxesProvisioner,
                     methods: Set[Method]) extends JMAPRoutes {

  private val methodsByName: Map[MethodName, Method] = methods.map(method => method.methodName -> method).toMap

  @Inject
  def this(@Named(InjectionKeys.RFC_8621) authenticator: Authenticator,
           serializer: Serializer,
           userProvisioner: UserProvisioning,
           mailboxesProvisioner: MailboxesProvisioner,
           javaMethods: java.util.Set[Method]) {
    this(authenticator, serializer, userProvisioner, mailboxesProvisioner, javaMethods.asScala.toSet)
  }

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.POST, JMAP))
      .action(this.post)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, JMAP))
      .action(JMAPRoutes.CORS_CONTROL)
      .corsHeaders())

  private def post(httpServerRequest: HttpServerRequest, httpServerResponse: HttpServerResponse): Mono[Void] =
    SMono(authenticator.authenticate(httpServerRequest))
      .flatMap((mailboxSession: MailboxSession) => SFlux.merge(Seq(
          userProvisioner.provisionUser(mailboxSession),
          mailboxesProvisioner.createMailboxesIfNeeded(mailboxSession)))
        .`then`
        .`then`(this.requestAsJsonStream(httpServerRequest)
          .flatMap(requestObject => this.process(requestObject, httpServerResponse, mailboxSession))))
      .onErrorResume(throwable => handleError(throwable, httpServerResponse))
      .subscribeOn(Schedulers.elastic)
      .asJava()
      .`then`()

  private def requestAsJsonStream(httpServerRequest: HttpServerRequest): SMono[RequestObject] = {
    SMono.fromPublisher(httpServerRequest
      .receive()
      .aggregate()
      .asInputStream())
      .flatMap(this.parseRequestObject)
  }

  private def parseRequestObject(inputStream: InputStream): SMono[RequestObject] =
    serializer.deserializeRequestObject(inputStream) match {
      case JsSuccess(requestObject, _) => SMono.just(requestObject)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(serializer.serialize(errors).toString()))
    }

  private def process(requestObject: RequestObject,
                      httpServerResponse: HttpServerResponse,
                      mailboxSession: MailboxSession): SMono[Void] = {
    val processingContext: ProcessingContext = new ProcessingContext
    val unsupportedCapabilities = requestObject.using.toSet -- DefaultCapabilities.SUPPORTED.ids
    val capabilities: Set[CapabilityIdentifier] = requestObject.using.toSet

    if (unsupportedCapabilities.nonEmpty) {
      SMono.raiseError(UnsupportedCapabilitiesException(unsupportedCapabilities))
    } else {
      requestObject
        .methodCalls
        .map(invocation => process(capabilities, mailboxSession, processingContext, invocation))
        .foldLeft(SFlux.empty[Invocation]) { (flux: SFlux[Invocation], mono: SMono[Invocation]) => flux.concatWith(mono) }
        .collectSeq()
        .flatMap((invocations: Seq[Invocation]) =>
          SMono.fromPublisher(httpServerResponse.status(OK)
            .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
            .sendString(
              SMono.fromCallable(() =>
                serializer.serialize(ResponseObject(ResponseObject.SESSION_STATE, invocations)).toString),
              StandardCharsets.UTF_8)
            .`then`())
        )
    }
  }

  private def process(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession, processingContext: ProcessingContext, invocation: Invocation) = {
    SMono.defer(() => {
      processingContext.resolveBackReferences(serializer, invocation) match {
        case Left(e) => SMono.just(Invocation.error(
          errorCode = ErrorCode.InvalidResultReference,
          description = s"Failed resolving back-reference: ${e.message}",
          methodCallId = invocation.methodCallId))
        case Right(resolvedInvocation) => processMethodWithMatchName(capabilities, resolvedInvocation, mailboxSession, processingContext)
          .doOnNext(invocationResult => processingContext.recordInvocation(invocationResult))
      }
    })
  }

  private def processMethodWithMatchName(capabilities: Set[CapabilityIdentifier], invocation: Invocation, mailboxSession: MailboxSession, processingContext: ProcessingContext): SMono[Invocation] =
    SMono.justOrEmpty(methodsByName.get(invocation.methodName))
      .flatMap(method => SMono.fromPublisher(method.process(capabilities, invocation, mailboxSession, processingContext)))
      .onErrorResume(throwable => SMono.just(Invocation.error(ErrorCode.ServerFail, throwable.getMessage, invocation.methodCallId)))
      .switchIfEmpty(SMono.just(Invocation.error(ErrorCode.UnknownMethod, invocation.methodCallId)))

  private def handleError(throwable: Throwable, httpServerResponse: HttpServerResponse): SMono[Void] = throwable match {
    case exception: IllegalArgumentException => respondDetails(httpServerResponse,
      notRequestProblem(
        s"The request was successfully parsed as JSON but did not match the type signature of the Request object: ${exception.getMessage}"))
    case exception: UnauthorizedException => SMono(handleAuthenticationFailure(httpServerResponse, JMAPApiRoutes.LOGGER, exception))
    case exception: JsonParseException => respondDetails(httpServerResponse,
      notJSONProblem(
        s"The content type of the request was not application/json or the request did not parse as I-JSON: ${exception.getMessage}"))
    case exception: UnsupportedCapabilitiesException => respondDetails(httpServerResponse,
      unknownCapabilityProblem(s"The request used unsupported capabilities: ${exception.capabilities}"))
    case _ => SMono.fromPublisher(handleInternalError(httpServerResponse, JMAPApiRoutes.LOGGER, throwable))
  }

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails): SMono[Void] =
    SMono.fromPublisher(httpServerResponse.status(SC_BAD_REQUEST)
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .sendString(SMono.fromCallable(() => serializer.serialize(details).toString),
        StandardCharsets.UTF_8)
      .`then`)
}

case class UnsupportedCapabilitiesException(capabilities: Set[CapabilityIdentifier]) extends RuntimeException
