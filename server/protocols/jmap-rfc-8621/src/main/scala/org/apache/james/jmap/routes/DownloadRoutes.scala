/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jmap.routes

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.util.stream
import java.util.stream.Stream

import com.google.common.base.CharMatcher
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpResponseStatus.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, NOT_FOUND, OK, UNAUTHORIZED}
import io.netty.handler.codec.http.{HttpMethod, HttpResponseStatus, QueryStringDecoder}
import javax.inject.{Inject, Named}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Id, ProblemDetails}
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.mail.Email.Size
import org.apache.james.jmap.mail.{BlobId, EmailBodyPart, PartId}
import org.apache.james.jmap.routes.DownloadRoutes.{BUFFER_SIZE, LOGGER}
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.model.{AttachmentId, AttachmentMetadata, ContentType, FetchGroup, MessageId, MessageResult}
import org.apache.james.mailbox.{AttachmentManager, MailboxSession, MessageIdManager}
import org.apache.james.mime4j.codec.EncoderUtil
import org.apache.james.mime4j.codec.EncoderUtil.Usage
import org.apache.james.mime4j.message.DefaultMessageWriter
import org.apache.james.util.ReactorUtils
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import scala.compat.java8.FunctionConverters._
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object DownloadRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[DownloadRoutes])

  val BUFFER_SIZE: Int = 16 * 1024
}

sealed trait BlobResolutionResult {
  def asOption: Option[SMono[Blob]]
}
case class NonApplicable() extends BlobResolutionResult {
  override def asOption: Option[SMono[Blob]] = None
}
case class Applicable(blob: SMono[Blob]) extends BlobResolutionResult {
  override def asOption: Option[SMono[Blob]] = Some(blob)
}

trait BlobResolver {
  def resolve(blobId: BlobId, mailboxSession: MailboxSession): BlobResolutionResult
}

trait Blob {
  def blobId: BlobId
  def contentType: ContentType
  def size: Try[Size]
  def content: InputStream
}

case class BlobNotFoundException(blobId: BlobId) extends RuntimeException
case class ForbiddenException() extends RuntimeException

case class MessageBlob(blobId: BlobId, message: MessageResult) extends Blob {
  override def contentType: ContentType = new ContentType("message/rfc822")

  override def size: Try[Size] = refineV[NonNegative](message.getSize) match {
    case Left(e) => Failure(new IllegalArgumentException(e))
    case Right(size) => Success(size)
  }

  override def content: InputStream = message.getFullContent.getInputStream
}

case class AttachmentBlob(attachmentMetadata: AttachmentMetadata, fileContent: InputStream) extends Blob {
  override def size: Try[Size] = Success(UploadRoutes.sanitizeSize(attachmentMetadata.getSize))

  override def contentType: ContentType = attachmentMetadata.getType

  override def content: InputStream = fileContent

  override def blobId: BlobId = BlobId.of(attachmentMetadata.getAttachmentId.getId).get
}

case class EmailBodyPartBlob(blobId: BlobId, part: EmailBodyPart) extends Blob {
  override def size: Try[Size] = Success(part.size)

  override def contentType: ContentType = new ContentType(part.`type`.value)

  override def content: InputStream = {
    val writer = new DefaultMessageWriter
    val outputStream = new ByteArrayOutputStream()
    writer.writeBody(part.entity.getBody, outputStream)
    new ByteArrayInputStream(outputStream.toByteArray)
  }
}

class MessageBlobResolver @Inject()(val messageIdFactory: MessageId.Factory,
                                    val messageIdManager: MessageIdManager) extends BlobResolver {
  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): BlobResolutionResult = {
    Try(messageIdFactory.fromString(blobId.value.value)) match {
      case Failure(_) => NonApplicable()
      case Success(messageId) => Applicable(SMono.fromPublisher(
        messageIdManager.getMessagesReactive(List(messageId).asJava, FetchGroup.FULL_CONTENT, mailboxSession))
        .map[Blob](MessageBlob(blobId, _))
        .switchIfEmpty(SMono.error(BlobNotFoundException(blobId))))
    }
  }
}

class AttachmentBlobResolver @Inject()(val attachmentManager: AttachmentManager) extends BlobResolver {
  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): BlobResolutionResult =
    AttachmentId.from(org.apache.james.mailbox.model.BlobId.fromString(blobId.value.value)) match {
      case attachmentId: AttachmentId =>
        Try(attachmentManager.getAttachment(attachmentId, mailboxSession)) match {
          case Success(attachmentMetadata) => Applicable(
            SMono.fromCallable(() => AttachmentBlob(attachmentMetadata, attachmentManager.load(attachmentMetadata, mailboxSession))))
          case Failure(_) => Applicable(SMono.error(BlobNotFoundException(blobId)))
        }

      case _ => NonApplicable()
    }
}

class MessagePartBlobResolver @Inject()(val messageIdFactory: MessageId.Factory,
                                        val messageIdManager: MessageIdManager) extends BlobResolver {
  private def asMessageAndPartId(blobId: BlobId): Try[(MessageId, PartId)] = {
    blobId.value.value.split("_").toList match {
      case List(messageIdString, partIdString) => for {
        messageId <- Try(messageIdFactory.fromString(messageIdString))
        partId <- PartId.parse(partIdString)
      } yield {
        (messageId, partId)
      }
      case _ => Failure(BlobNotFoundException(blobId))
    }
  }

  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): BlobResolutionResult = {
    asMessageAndPartId(blobId) match {
      case Failure(_) => NonApplicable()
      case Success((messageId, partId)) =>
        Applicable(SMono.fromPublisher(
          messageIdManager.getMessagesReactive(List(messageId).asJava, FetchGroup.FULL_CONTENT, mailboxSession))
          .handle[EmailBodyPart] {
            case (message, sink) => EmailBodyPart.of(messageId, message)
              .fold(sink.error, sink.next)
          }
          .handle[EmailBodyPart] {
            case (bodyStructure, sink) =>
              bodyStructure.flatten
                .find(_.blobId.contains(blobId))
                .fold(sink.error(BlobNotFoundException(blobId)))(part => sink.next(part))
          }
          .map[Blob](EmailBodyPartBlob(blobId, _))
          .switchIfEmpty(SMono.error(BlobNotFoundException(blobId))))
    }
  }
}

class BlobResolvers @Inject()(val messageBlobResolver: MessageBlobResolver,
                              val messagePartBlobResolver: MessagePartBlobResolver,
                              val attachmentBlobResolver: AttachmentBlobResolver) {
  def resolve(blobId: BlobId, mailboxSession: MailboxSession): SMono[Blob] =
    messageBlobResolver
      .resolve(blobId, mailboxSession).asOption
      .orElse(messagePartBlobResolver.resolve(blobId, mailboxSession).asOption)
      .orElse(attachmentBlobResolver.resolve(blobId, mailboxSession).asOption)
      .getOrElse(SMono.error(BlobNotFoundException(blobId)))
}

class DownloadRoutes @Inject()(@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                               val blobResolvers: BlobResolvers) extends JMAPRoutes {

  private val accountIdParam: String = "accountId"
  private val blobIdParam: String = "blobId"
  private val nameParam: String = "name"
  private val contentTypeParam: String = "contentType"
  private val downloadUri = s"/download/{$accountIdParam}/{$blobIdParam}"

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.GET, downloadUri))
      .action(this.get)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, downloadUri))
      .action(JMAPRoutes.CORS_CONTROL)
      .noCorsHeaders)

  private def get(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    SMono(authenticator.authenticate(request))
      .flatMap(mailboxSession => getIfOwner(request, response, mailboxSession))
      .onErrorResume {
        case e: ForbiddenException =>
          respondDetails(response,
            ProblemDetails(status = FORBIDDEN, detail = "You cannot download in others accounts"),
            FORBIDDEN)
        case e: UnauthorizedException =>
          LOGGER.warn("Unauthorized", e)
          respondDetails(e.addHeaders(response),
            ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage),
            UNAUTHORIZED)
        case _: BlobNotFoundException =>
          respondDetails(response,
            ProblemDetails(status = NOT_FOUND, detail = "The resource could not be found"),
            NOT_FOUND)
        case e =>
          LOGGER.error("Unexpected error upon downloads", e)
          respondDetails(response,
            ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage),
            INTERNAL_SERVER_ERROR)
      }
      .subscribeOn(Schedulers.elastic)
      .asJava()
      .`then`

  private def get(request: HttpServerRequest, response: HttpServerResponse, mailboxSession: MailboxSession): SMono[Unit] = {
    BlobId.of(request.param(blobIdParam))
      .fold(e => SMono.error(e),
        blobResolvers.resolve(_, mailboxSession))
      .flatMap(blob => downloadBlob(
        optionalName = queryParam(request, nameParam),
        response = response,
        blobContentType = queryParam(request, contentTypeParam)
          .map(ContentType.of)
          .getOrElse(blob.contentType),
        blob = blob)
        .`then`())
  }

  private def getIfOwner(request: HttpServerRequest, response: HttpServerResponse, mailboxSession: MailboxSession): SMono[Unit] = {
    Id.validate(request.param(accountIdParam)) match {
      case Right(id: Id) => {
        val targetAccountId: AccountId = AccountId(id)
        AccountId.from(mailboxSession.getUser).map(accountId => accountId.equals(targetAccountId))
          .fold[SMono[Unit]](
            e => SMono.error(e),
            value => if (value) {
              get(request, response, mailboxSession)
            } else {
              SMono.error(ForbiddenException())
            })
      }

      case Left(throwable: Throwable) => SMono.error(throwable)
    }
  }

  private def downloadBlob(optionalName: Option[String],
                           response: HttpServerResponse,
                           blobContentType: ContentType,
                           blob: Blob): SMono[Unit] =
    SMono.fromPublisher(Mono.using(
      () => blob.content,
      (stream: InputStream) => addContentDispositionHeader(optionalName)
        .compose(addContentLengthHeader(blob.size))
        .apply(response)
        .header(CONTENT_TYPE, blobContentType.asString)
        .status(OK)
        .sendByteArray(ReactorUtils.toChunks(stream, BUFFER_SIZE)
          .subscribeOn(Schedulers.elastic))
        .`then`,
      asJavaConsumer[InputStream]((stream: InputStream) => stream.close())))
      .`then`

  private def addContentDispositionHeader(optionalName: Option[String]): HttpServerResponse => HttpServerResponse =
    resp => optionalName.map(addContentDispositionHeaderRegardingEncoding(_, resp))
      .getOrElse(resp)

  private def addContentLengthHeader(sizeTry: Try[Size]): HttpServerResponse => HttpServerResponse =
    resp => sizeTry
      .map(size => resp.header("Content-Length", size.value.toString))
      .getOrElse(resp)

  private def addContentDispositionHeaderRegardingEncoding(name: String, resp: HttpServerResponse): HttpServerResponse =
    if (CharMatcher.ascii.matchesAllOf(name)) {
      resp.header("Content-Disposition", "attachment; filename=\"" + name + "\"")
    } else {
      resp.header("Content-Disposition", "attachment; filename*=\"" + EncoderUtil.encodeEncodedWord(name, Usage.TEXT_TOKEN) + "\"")
    }

  private def queryParam(httpRequest: HttpServerRequest, parameterName: String): Option[String] =
    queryParam(parameterName, httpRequest.uri)

  private def queryParam(parameterName: String, uri: String): Option[String] =
    Option(new QueryStringDecoder(uri).parameters.get(parameterName))
      .toList
      .flatMap(_.asScala)
      .headOption

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails, statusCode: HttpResponseStatus = BAD_REQUEST): SMono[Unit] =
    SMono.fromPublisher(httpServerResponse.status(statusCode)
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .sendString(SMono.fromCallable(() => ResponseSerializer.serialize(details).toString), StandardCharsets.UTF_8)
      .`then`).`then`
}
