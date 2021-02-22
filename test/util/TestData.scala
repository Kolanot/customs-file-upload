/*
 * Copyright 2021 HM Revenue & Customs
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

package util

import java.net.URL
import java.time.Instant
import java.util.UUID
import java.util.UUID.fromString

import com.google.inject.AbstractModule
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.HeaderNames._
import play.api.http.MimeTypes
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsXml
import play.api.test.FakeRequest
import play.api.test.Helpers.CONTENT_TYPE
import uk.gov.hmrc.customs.file.upload.model._
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.file.upload.model.actionbuilders._
import uk.gov.hmrc.customs.file.upload.services.{UniqueIdsService, UuidService}
import util.ApiSubscriptionFieldsTestData.subscriptionFieldsId
import util.TestData.declarantEori

import scala.xml.{Elem, NodeSeq}

object TestData {
  val conversationIdValue = "38400000-8cf0-11bd-b23e-10b96e4ef00d"
  val conversationIdUuid: UUID = fromString(conversationIdValue)
  val conversationId: ConversationId = ConversationId(conversationIdUuid)

  val correlationIdValue = "e61f8eee-812c-4b8f-b193-06aedc60dca2"
  val correlationIdUuid: UUID = fromString(correlationIdValue)
  val correlationId = CorrelationId(correlationIdUuid)

  val clientSubscriptionIdString: String = "327d9145-4965-4d28-a2c5-39dedee50334"

  val TenMb = 10485760
  val fileUploadConfig = FileUploadConfig("API_SUBSCRIPTION_FIELDS_URL", "CUSTOMS_NOTIFICATION_URL",
    "some-token", "UPSCAN_INITIATE_V1_URL", "UPSCAN_INITIATE_V2_URL",
    TenMb, "UPSCAN_URL_IGNORED", 3, "fileTransmissionCallbackUrl",  "fileTransmissionUrl", 60)

  val validBadgeIdentifierValue = "BADGEID123"
  val badgeIdentifier: BadgeIdentifier = BadgeIdentifier(validBadgeIdentifierValue)

  val cspBearerToken = "CSP-Bearer-Token"
  val nonCspBearerToken = "Software-House-Bearer-Token"

  val declarantEoriValue = "ZZ123456789000"
  val declarantEori = Eori(declarantEoriValue)
  val ONE = 1
  val TWO = 2
  val THREE = 3
  val FOUR = 4

  val ValidatedFileUploadPayloadRequestForNonCspWithTwoFiles = ValidatedFileUploadPayloadRequest(
    ConversationId(UUID.randomUUID()),
    VersionOne,
    ClientId("ABC"),
    NonCsp(Eori("123")),
    NodeSeq.Empty,
    FakeRequest().withJsonBody(Json.obj("fake" -> "request")),
    FileUploadRequest(DeclarationId("decId123"),FileGroupSize(TWO),
    Seq(FileUploadFile(FileSequenceNo(ONE), maybeDocumentType = None, Some("https://success-redirect.com"), Some("https://error-redirect.com")),
      FileUploadFile(FileSequenceNo(TWO), Some(DocumentType("docType2")), Some("https://success-redirect.com"), Some("https://error-redirect.com"))))
  )

  val ValidatedFileUploadPayloadRequestForCspWithTwoFiles = ValidatedFileUploadPayloadRequest(ConversationId(UUID.randomUUID()), VersionOne, ClientId("ABC"), CspWithEori(badgeIdentifier, Eori("123")), NodeSeq.Empty, FakeRequest().withJsonBody(Json.obj("fake" -> "request")), FileUploadRequest(DeclarationId("decId123"),FileGroupSize(TWO),
      Seq(FileUploadFile(FileSequenceNo(ONE), Some(DocumentType("docType1")), Some("https://success-redirect.com"), Some("https://error-redirect.com")),
        FileUploadFile(FileSequenceNo(TWO), Some(DocumentType("docType2")), Some("https://success-redirect.com"), Some("https://error-redirect.com")))))

  val ValidatedFileUploadPayloadRequestWithFourFiles = ValidatedFileUploadPayloadRequest(
    ConversationId(UUID.randomUUID()),
    VersionOne,
    ClientId("ABC"),
    NonCsp(Eori("123")),
    NodeSeq.Empty,
    FakeRequest().withJsonBody(Json.obj("fake" -> "request")),
    FileUploadRequest(
      DeclarationId("decId123"),
      FileGroupSize(FOUR),
      Seq(FileUploadFile(FileSequenceNo(ONE), maybeDocumentType = None, Some("https://success-redirect.com"), Some("https://error-redirect.com")),
        FileUploadFile(FileSequenceNo(TWO), Some(DocumentType("docType2")), Some("https://success-redirect.com"), Some("https://error-redirect.com")),
        FileUploadFile(FileSequenceNo(THREE), Some(DocumentType("docType3")), Some("https://success-redirect.com"), Some("https://error-redirect.com")),
        FileUploadFile(FileSequenceNo(FOUR), Some(DocumentType("docType4")), Some("https://success-redirect.com"), Some("https://error-redirect.com"))))
  )

  val TWENTY_FIVE = 25

  type EmulatedServiceFailure = UnsupportedOperationException
  val emulatedServiceFailure = new EmulatedServiceFailure("Emulated service failure.")

  lazy val mockUuidService: UuidService = mock[UuidService]

  object TestModule extends AbstractModule {
    override def configure(): Unit = {
      bind(classOf[UuidService]) toInstance mockUuidService
    }

    def asGuiceableModule: GuiceableModule = GuiceableModule.guiceable(this)
  }

  // note we can not mock service methods that return value classes - however using a simple stub IMHO it results in cleaner code (less mocking noise)
  lazy val stubUniqueIdsService: UniqueIdsService = new UniqueIdsService(mockUuidService) {
    override def conversation: ConversationId = conversationId
    override def correlation: CorrelationId = correlationId
  }

  val TestXmlPayload: Elem = <foo>bar</foo>
  val TestFakeRequest: FakeRequest[AnyContentAsXml] = FakeRequest().withXmlBody(TestXmlPayload).withHeaders(("Authorization", "bearer-token"))
  val TestFakeRequestWithBadgeIdAndNoEori: FakeRequest[AnyContentAsXml] = FakeRequest().withXmlBody(TestXmlPayload).withHeaders(("Authorization", "bearer-token"), ("X-Badge-Identifier", badgeIdentifier.value))
  val TestFakeRequestWithEoriAndNoBadgeId: FakeRequest[AnyContentAsXml] = FakeRequest().withXmlBody(TestXmlPayload).withHeaders(("Authorization", "bearer-token"), ("X-EORI-Identifier", declarantEori.value))

  def testFakeRequestWithBadgeIdEoriPair(badgeIdString: String = badgeIdentifier.value, eoriString: String = declarantEori.value): FakeRequest[AnyContentAsXml] =
    FakeRequest().withXmlBody(TestXmlPayload).withHeaders(RequestHeaders.X_BADGE_IDENTIFIER_NAME -> badgeIdString, RequestHeaders.X_EORI_IDENTIFIER_NAME -> eoriString)

  val TestConversationIdRequest = ConversationIdRequest(conversationId, TestFakeRequest)
  val TestConversationIdRequestWithBadgeIdAndNoEori = ConversationIdRequest(conversationId, TestFakeRequestWithBadgeIdAndNoEori)
  val TestConversationIdRequestWithEoriAndNoBadgeId = ConversationIdRequest(conversationId, TestFakeRequestWithEoriAndNoBadgeId)
  val TestExtractedHeaders = ExtractedHeadersImpl(VersionOne, ApiSubscriptionFieldsTestData.clientId)
  val TestValidatedHeadersRequest: ValidatedHeadersRequest[AnyContentAsXml] = TestConversationIdRequest.toValidatedHeadersRequest(TestExtractedHeaders)

  val TestCspAuthorisedRequest: AuthorisedRequest[AnyContentAsXml] = TestValidatedHeadersRequest.toCspAuthorisedRequest(Csp(badgeIdentifier))
  val TestValidatedHeadersRequestNoBadge: ValidatedHeadersRequest[AnyContentAsXml] = TestConversationIdRequest.toValidatedHeadersRequest(TestExtractedHeaders)
  val TestValidatedHeadersRequestWithBadgeIdAndNoEori: ValidatedHeadersRequest[AnyContentAsXml] = TestConversationIdRequestWithBadgeIdAndNoEori.toValidatedHeadersRequest(TestExtractedHeaders)
  val TestValidatedHeadersRequestWithEoriAndNoBadgeId: ValidatedHeadersRequest[AnyContentAsXml] = TestConversationIdRequestWithEoriAndNoBadgeId.toValidatedHeadersRequest(TestExtractedHeaders)
  val TestCspValidatedPayloadRequest: ValidatedPayloadRequest[AnyContentAsXml] = TestValidatedHeadersRequest.toCspAuthorisedRequest(Csp(badgeIdentifier)).toValidatedPayloadRequest(xmlBody = TestXmlPayload)

  val BatchIdOne = BatchId(fromString("48400000-8cf0-11bd-b23e-10b96e4ef001"))
  val BatchIdTwo = BatchId(fromString("48400000-8cf0-11bd-b23e-10b96e4ef002"))
  val BatchIdThree = BatchId(fromString("48400000-8cf0-11bd-b23e-10b96e4ef003"))
  val FileReferenceOne = FileReference(fromString("31400000-8ce0-11bd-b23e-10b96e4ef00f"))
  val FileReferenceTwo = FileReference(fromString("32400000-8cf0-11bd-b23e-10b96e4ef00f"))
  val FileReferenceThree = FileReference(fromString("33400000-8cd0-11bd-b23e-10b96e4ef00f"))
  val InitiateDateAsString = "2018-04-24T09:30:00Z"
  val InitiateDate = Instant.parse(InitiateDateAsString)
  val createdAtDate = new DateTime(InitiateDate.toEpochMilli, DateTimeZone.UTC)
  val CallbackFieldsOne = CallbackFields("name1", "application/xml", "checksum1", InitiateDate, new URL("https://outbound.a.com"))
  val CallbackFieldsTwo = CallbackFields("name2", "application/xml", "checksum2", InitiateDate, new URL("https://outbound.a.com"))
  val CallbackFieldsThree = CallbackFields("name3", "application/xml", "checksum3", InitiateDate, new URL("https://outbound.a.com"))
  val CallbackFieldsUpdated = CallbackFields("UPDATED_NAME", "UPDATED_MIMETYPE", "UPDATED_CHECKSUM", InitiateDate, new URL("https://outbound.a.com"))
  val BatchFileOne = BatchFile(reference = FileReferenceOne, Some(CallbackFieldsOne),
    inboundLocation = new URL("https://a.b.com"), sequenceNumber = FileSequenceNo(1), size = 1, documentType = Some(DocumentType("Document Type 1")))
  val BatchFileTwo = BatchFile(reference = FileReferenceTwo, Some(CallbackFieldsTwo),
    inboundLocation = new URL("https://a.b.com"), sequenceNumber = FileSequenceNo(2), size = 1, documentType = Some(DocumentType("Document Type 2")))
  val BatchFileThree = BatchFile(reference = FileReferenceThree, Some(CallbackFieldsThree),
    inboundLocation = new URL("https://a.b.com"), sequenceNumber = FileSequenceNo(3), size = 1, documentType = Some(DocumentType("Document Type 3")))
  val FileMetadataWithFileOne = FileUploadMetadata(DeclarationId("1"), Eori("123"), csId = subscriptionFieldsId, BatchIdOne, fileCount = 1, createdAt = createdAtDate, Seq(
    BatchFileOne
  ))
  val FileMetadataWithFileTwo = FileUploadMetadata(DeclarationId("2"), Eori("123"), csId = subscriptionFieldsId, BatchIdTwo, fileCount = 1, createdAt = createdAtDate, Seq(
    BatchFileTwo
  ))
  val FileMetadataWithFilesOneAndThree = FileUploadMetadata(DeclarationId("3"), Eori("123"), csId = subscriptionFieldsId, BatchIdThree, fileCount = 2, createdAt = createdAtDate, Seq(
    BatchFileOne, BatchFileThree
  ))

}

object RequestHeaders {

  val X_CONVERSATION_ID_NAME = "X-Conversation-ID"

  val X_BADGE_IDENTIFIER_NAME = "X-Badge-Identifier"
  lazy val X_BADGE_IDENTIFIER_HEADER: (String, String) = X_BADGE_IDENTIFIER_NAME -> TestData.badgeIdentifier.value
   
  val X_CLIENT_ID_NAME = "X-Client-ID"
  val X_CLIENT_ID_HEADER: (String, String) = X_CLIENT_ID_NAME -> ApiSubscriptionFieldsTestData.xClientId
  val X_CLIENT_ID_HEADER_INVALID: (String, String) = X_CLIENT_ID_NAME -> "This is not a UUID"

  val X_EORI_IDENTIFIER_NAME = "X-EORI-Identifier"

  val X_SUBMITTER_IDENTIFIER_NAME = "X-Submitter-Identifier"
  val X_SUBMITTER_IDENTIFIER_HEADER: (String, String) = X_SUBMITTER_IDENTIFIER_NAME -> declarantEori.value

  val CONTENT_TYPE_HEADER: (String, String) = CONTENT_TYPE -> MimeTypes.XML
  val CONTENT_TYPE_CHARSET_VALUE: String = s"${MimeTypes.XML}; charset=UTF-8"
  val CONTENT_TYPE_HEADER_INVALID: (String, String) = CONTENT_TYPE -> "somethinginvalid"

  val ACCEPT_HMRC_XML_V1_VALUE = "application/vnd.hmrc.1.0+xml"
  val ACCEPT_HMRC_XML_V1_HEADER: (String, String) = ACCEPT -> ACCEPT_HMRC_XML_V1_VALUE
  val ACCEPT_HEADER_INVALID: (String, String) = ACCEPT -> "invalid"

  val ValidHeadersV1: Map[String, String] = Map(
    CONTENT_TYPE_HEADER,
    ACCEPT_HMRC_XML_V1_HEADER,
    X_CLIENT_ID_HEADER,
    X_BADGE_IDENTIFIER_HEADER,
    X_SUBMITTER_IDENTIFIER_HEADER
  )
}
