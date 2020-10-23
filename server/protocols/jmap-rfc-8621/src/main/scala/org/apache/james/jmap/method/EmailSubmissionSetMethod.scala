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

package org.apache.james.jmap.method

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.http.SessionSupplier
import org.apache.james.jmap.json.{EmailSubmissionSetSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.EmailSubmissionSet.EmailSubmissionCreationId
import org.apache.james.jmap.mail.{EmailSubmissionCreationRequest, EmailSubmissionCreationResponse, EmailSubmissionId, EmailSubmissionSetRequest, EmailSubmissionSetResponse}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.DefaultCapabilities.{CORE_CAPABILITY, MAIL_CAPABILITY}
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.SetError.SetErrorDescription
import org.apache.james.jmap.model.{Capabilities, ClientId, Id, Invocation, ServerId, SetError, State}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsObject, JsPath, JsSuccess, Json, JsonValidationError}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

case class EmailSubmissionCreationParseException(setError: SetError) extends Exception

class EmailSubmissionSetMethod @Inject()(serializer: EmailSubmissionSetSerializer,
                                         messageIdManager: MessageIdManager,
                                         val metricFactory: MetricFactory,
                                         val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[EmailSubmissionSetRequest] {
  override val methodName: MethodName = MethodName("EmailSubmission/set")
  override val requiredCapabilities: Capabilities = Capabilities(CORE_CAPABILITY, MAIL_CAPABILITY)

  sealed trait CreationResult {
    def emailSubmissionCreationId: EmailSubmissionCreationId
  }
  case class CreationSuccess(emailSubmissionCreationId: EmailSubmissionCreationId, emailSubmissionCreationResponse: EmailSubmissionCreationResponse) extends CreationResult
  case class CreationFailure(emailSubmissionCreationId: EmailSubmissionCreationId, exception: Exception) extends CreationResult
  case class CreationResults(created: Seq[CreationResult]) {
    def retrieveCreated: Map[EmailSubmissionCreationId, EmailSubmissionCreationResponse] = created
      .flatMap(result => result match {
        case success: CreationSuccess => Some(success.emailSubmissionCreationId, success.emailSubmissionCreationResponse)
        case _ => None
      })
      .toMap
      .map(creation => (creation._1, creation._2))
  }

    override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailSubmissionSetRequest): SMono[InvocationWithContext] = {
    for {
      createdResults <- create(request, mailboxSession, invocation.processingContext)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = invocation.invocation.methodName,
        arguments = Arguments(serializer.serializeEmailSubmissionSetResponse(EmailSubmissionSetResponse(
            accountId = request.accountId,
            newState = State.INSTANCE,
            created = Some(createdResults._1.retrieveCreated)))
          .as[JsObject]),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = createdResults._2)
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): SMono[EmailSubmissionSetRequest] =
    asEmailSubmissionSetRequest(invocation.arguments)

  private def asEmailSubmissionSetRequest(arguments: Arguments): SMono[EmailSubmissionSetRequest] =
    serializer.deserializeEmailSubmissionSetRequest(arguments.value) match {
      case JsSuccess(emailSubmissionSetRequest, _) => SMono.just(emailSubmissionSetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  private def create(request: EmailSubmissionSetRequest,
                     session: MailboxSession,
                     processingContext: ProcessingContext): SMono[(CreationResults, ProcessingContext)] = {
    SFlux.fromIterable(request.create
      .getOrElse(Map.empty)
      .view)
      .foldLeft((CreationResults(Nil), processingContext)){
        (acc : (CreationResults, ProcessingContext), elem: (EmailSubmissionCreationId, JsObject)) => {
          val (emailSubmissionCreationId, jsObject) = elem
          val (creationResult, updatedProcessingContext) = createMailbox(session, emailSubmissionCreationId, jsObject, acc._2)
          (CreationResults(acc._1.created :+ creationResult), updatedProcessingContext)
        }
      }
      .subscribeOn(Schedulers.elastic())
  }

  private def createMailbox(mailboxSession: MailboxSession,
                            emailSubmissionCreationId: EmailSubmissionCreationId,
                            jsObject: JsObject,
                            processingContext: ProcessingContext): (CreationResult, ProcessingContext) = {
    parseCreate(jsObject)
      .flatMap(emailSubmissionCreationRequest => sendEmail(mailboxSession, emailSubmissionCreationRequest))
      .flatMap(creationResponse => recordCreationIdInProcessingContext(emailSubmissionCreationId, processingContext, creationResponse.id)
        .map(context => (creationResponse, context)))
      .fold(e => (CreationFailure(emailSubmissionCreationId, e), processingContext),
        creationResponseWithUpdatedContext => {
          (CreationSuccess(emailSubmissionCreationId, creationResponseWithUpdatedContext._1), creationResponseWithUpdatedContext._2)
        })
  }

  private def parseCreate(jsObject: JsObject): Either[EmailSubmissionCreationParseException, EmailSubmissionCreationRequest] =
    EmailSubmissionCreationRequest.validateProperties(jsObject)
      .flatMap(validJsObject => Json.fromJson(validJsObject)(serializer.emailSubmissionCreationRequestReads) match {
        case JsSuccess(creationRequest, _) => Right(creationRequest)
        case JsError(errors) => Left(EmailSubmissionCreationParseException(emailSubmissionSetError(errors)))
      })

  private def emailSubmissionSetError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): SetError =
    errors.head match {
      case (path, Seq()) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in EmailSubmission object is not valid"))
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => SetError.invalidArguments(SetErrorDescription(s"Missing '$path' property in EmailSubmission object"))
      case (path, Seq(JsonValidationError(Seq(message)))) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in EmailSubmission object is not valid: $message"))
      case (path, _) => SetError.invalidArguments(SetErrorDescription(s"Unknown error on property '$path'"))
    }

  private def sendEmail(mailboxSession: MailboxSession,
                        emailSubmissionCreationRequest: EmailSubmissionCreationRequest): Either[Exception, EmailSubmissionCreationResponse] = ???

  private def recordCreationIdInProcessingContext(emailSubmissionCreationId: EmailSubmissionCreationId,
                                                  processingContext: ProcessingContext,
                                                  emailSubmissionId: EmailSubmissionId): Either[IllegalArgumentException, ProcessingContext] = {
    for {
      creationId <- Id.validate(emailSubmissionCreationId)
      serverAssignedId <- Id.validate(emailSubmissionId.value)
    } yield {
      processingContext.recordCreatedId(ClientId(creationId), ServerId(serverAssignedId))
    }
  }
}
