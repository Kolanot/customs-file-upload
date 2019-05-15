/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.customs.file.upload.model

import java.util.UUID

import play.api.libs.json._

case class RequestedVersion(versionNumber: String, configPrefix: Option[String])

case class Eori(value: String) extends AnyVal {
  override def toString: String = value.toString
}
object Eori {
  implicit val writer: Writes[Eori] = Writes[Eori] { x => JsString(x.value) }
  implicit val reader: Reads[Eori] = Reads.of[String].map(new Eori(_))
}

case class BadgeIdentifierEoriPair(badgeIdentifier: BadgeIdentifier, eori: Eori)

case class ClientId(value: String) extends AnyVal {
  override def toString: String = value.toString
}

case class ConversationId(uuid: UUID) extends AnyVal {
  override def toString: String = uuid.toString
}
object ConversationId {
  implicit val writer: Writes[ConversationId] = Writes[ConversationId] { x => JsString(x.uuid.toString) }
  implicit val reader: Reads[ConversationId] = Reads.of[UUID].map(new ConversationId(_))
}

case class CorrelationId(uuid: UUID) extends AnyVal {
  override def toString: String = uuid.toString
}

case class BadgeIdentifier(value: String) extends AnyVal {
  override def toString: String = value.toString
}

case class SubscriptionFieldsId(value: UUID) extends AnyVal{
  override def toString: String = value.toString
}
object SubscriptionFieldsId {
  implicit val writer: Writes[SubscriptionFieldsId] = Writes[SubscriptionFieldsId] { x => JsString(x.value.toString) }
  implicit val reader: Reads[SubscriptionFieldsId] = Reads.of[UUID].map(new SubscriptionFieldsId(_))
}

case class DeclarationId(value: String) extends AnyVal{
  override def toString: String = value.toString
}
object DeclarationId {
  implicit val writer: Writes[DeclarationId] = Writes[DeclarationId] { x => JsString(x.value) }
  implicit val reader: Reads[DeclarationId] = Reads.of[String].map(new DeclarationId(_))
}

case class DocumentationType(value: String) extends AnyVal{
  override def toString: String = value.toString
}

object DocumentationType {
  implicit val writer: Writes[DocumentationType] = Writes[DocumentationType] { x => JsString(x.value) }
  implicit val reader: Reads[DocumentationType] = Reads.of[String].map(new DocumentationType(_))
}

case class FileSequenceNo(value: Int) extends AnyVal{
  override def toString: String = value.toString
}
object FileSequenceNo {
  implicit val writer: Writes[FileSequenceNo] = Writes[FileSequenceNo] { x =>
    val d: BigDecimal = x.value
    JsNumber(d)
  }
  implicit val reader: Reads[FileSequenceNo] = Reads.of[Int].map(new FileSequenceNo(_))
}

case class FileGroupSize(value: Int) extends AnyVal{
  override def toString: String = value.toString
}

sealed trait ApiVersion {
  val value: String
  val configPrefix: String
  override def toString: String = value
}
object VersionOne extends ApiVersion{
  override val value: String = "1.0"
  override val configPrefix: String = ""
}

sealed trait AuthorisedAs {
}
sealed trait AuthorisedAsCsp extends AuthorisedAs {
  val badgeIdentifier: BadgeIdentifier
}
case class Csp(badgeIdentifier: BadgeIdentifier) extends AuthorisedAsCsp
case class CspWithEori(badgeIdentifier: BadgeIdentifier, eori: Eori) extends AuthorisedAsCsp
case class NonCsp(eori: Eori) extends AuthorisedAs

case class UpscanInitiatePayload(callbackUrl: String, maximumFileSize: Int)

object UpscanInitiatePayload {
  implicit val format: OFormat[UpscanInitiatePayload] = Json.format[UpscanInitiatePayload]
}

case class AuthorisedRetrievalData(retrievalJSONBody: String)

case class UpscanInitiateResponsePayload(reference: String, uploadRequest: UpscanInitiateUploadRequest)

object UpscanInitiateUploadRequest {
  implicit val format: OFormat[UpscanInitiateUploadRequest] = Json.format[UpscanInitiateUploadRequest]
}

case class UpscanInitiateUploadRequest
(
  href: String,
  fields: Map[String, String]
)

object UpscanInitiateResponsePayload {
  implicit val format: OFormat[UpscanInitiateResponsePayload] = Json.format[UpscanInitiateResponsePayload]
}
