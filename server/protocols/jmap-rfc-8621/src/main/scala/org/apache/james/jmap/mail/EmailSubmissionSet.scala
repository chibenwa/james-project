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

package org.apache.james.jmap.mail

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.mail.Email.UnparsedEmailId
import org.apache.james.jmap.mail.EmailSubmissionSet.EmailSubmissionCreationId
import org.apache.james.jmap.method.{EmailSubmissionCreationParseException, WithAccountId}
import org.apache.james.jmap.model.SetError.SetErrorDescription
import org.apache.james.jmap.model.State.State
import org.apache.james.jmap.model.{AccountId, Properties, SetError}
import play.api.libs.json.JsObject

object EmailSubmissionSet {
  type EmailSubmissionCreationId = String Refined NonEmpty
}

case class EmailSubmissionSetRequest(accountId: AccountId,
                                     create: Option[Map[EmailSubmissionCreationId, JsObject]]) extends WithAccountId

case class EmailSubmissionSetResponse(accountId: AccountId,
                                      newState: State,
                                      created: Option[Map[EmailSubmissionCreationId, EmailSubmissionCreationResponse]])

case class EmailSubmissionId(value: String)

case class EmailSubmissionCreationResponse(id: EmailSubmissionId)

case class Parameters(value: String)
case class EmailSubmissionAddress(email: Address, parameters: Option[Parameters])

case class Envelope(mailFrom: EmailSubmissionAddress, rcptTo: EmailSubmissionAddress)

object EmailSubmissionCreationRequest {
  private val assignableProperties = Set("name", "parentId", "isSubscribed", "rights")

  def validateProperties(jsObject: JsObject): Either[EmailSubmissionCreationParseException, JsObject] =
    jsObject.keys.diff(assignableProperties) match {
      case unknownProperties if unknownProperties.nonEmpty =>
        Left(EmailSubmissionCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(toProperties(unknownProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }

  private def toProperties(strings: Set[String]): Properties = Properties(strings
    .flatMap(string => {
      val refinedValue: Either[String, NonEmptyString] = refineV[NonEmpty](string)
      refinedValue.fold(_ => None,  Some(_))
    }))
}

case class EmailSubmissionCreationRequest(emailId: UnparsedEmailId,
                                          envelope: Envelope)