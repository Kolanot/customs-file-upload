/*
 * Copyright 2020 HM Revenue & Customs
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

package unit.controllers.actionBuilders

import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{ErrorInternalServerError, errorBadRequest}
import uk.gov.hmrc.customs.file.upload.controllers.CustomHeaderNames.{XBadgeIdentifierHeaderName, XEoriIdentifierHeaderName}
import uk.gov.hmrc.customs.file.upload.controllers.actionBuilders.{AuthAction, HeaderValidator}
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.CspWithEori
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.file.upload.model.actionbuilders._
import uk.gov.hmrc.customs.file.upload.services.{CustomsAuthService, FileUploadConfigService}
import util.TestData._
import util.{AuthConnectorStubbing, RequestHeaders, UnitSpec}

class AuthActionSpec extends UnitSpec with MockitoSugar {

  private val errorResponseBadgeIdentifierHeaderMissing =
    errorBadRequest(s"$XBadgeIdentifierHeaderName header is missing or invalid")
  private val errorResponseEoriIdentifierHeaderMissing =
    errorBadRequest(s"$XEoriIdentifierHeaderName header is missing or invalid")

  private lazy val validatedHeadersRequestWithValidBadgeIdEoriPair =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdEoriPair()).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithInvalidBadgeIdEoriPair =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdEoriPair(eoriString = "", badgeIdString = "")).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithValidBadgeIdAndEmptyEori =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdEoriPair(eoriString = "")).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithValidBadgeIdAndEoriTooLong =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdEoriPair(eoriString = "INVALID_EORI_TOO_LONG")).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithInvalidEoriInvalidChars =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdEoriPair(eoriString = "     ")).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithInvalidBadgeIdTooLong =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdEoriPair(badgeIdString = "INVALID_BADGE_IDENTIFIER_TOO_LONG")).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithInvalidBadgeIdLowerCase =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdEoriPair(badgeIdString = "lowercase")).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithInvalidBadgeIdTooShort =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdEoriPair(badgeIdString = "SHORT")).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithInvalidBadgeIdInvalidChars =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdEoriPair(badgeIdString = "(*&*(^&*&%")).toValidatedHeadersRequest(TestExtractedHeaders)

  trait SetUp extends AuthConnectorStubbing {
    private implicit val ec = Helpers.stubControllerComponents().executionContext
    val mockLogger: FileUploadLogger = mock[FileUploadLogger]
    val mockFileUploadConfigService: FileUploadConfigService = mock[FileUploadConfigService]
    protected val customsAuthService = new CustomsAuthService(mockAuthConnector, mockLogger)
    protected val headerValidator = new HeaderValidator(mockLogger)
    val fileUploadAuthAction = new AuthAction(customsAuthService, headerValidator, mockLogger, mockFileUploadConfigService)
  }

  "AuthAction Builder " can {
    "as CSP" should {
      "authorise as CSP when authorised by auth API and badge identifier exists" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(validatedHeadersRequestWithValidBadgeIdEoriPair))
        actual shouldBe Right(validatedHeadersRequestWithValidBadgeIdEoriPair.toCspAuthorisedRequest(CspWithEori(badgeIdentifier, declarantEori)))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 401 response when authorised by auth API but badge identifier does not exist" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(TestValidatedHeadersRequestNoBadge))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 401 response when authorised by auth API but badge identifier does not exist and eori does" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(TestValidatedHeadersRequestWithEoriAndNoBadgeId))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 401 response when authorised by auth API but badge identifier exists but is too long" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(validatedHeadersRequestWithInvalidBadgeIdTooLong))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 401 response when authorised by auth API but badge identifier exists but is too short" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(validatedHeadersRequestWithInvalidBadgeIdTooShort))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 401 response when authorised by auth API but badge identifier exists but contains invalid chars" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(validatedHeadersRequestWithInvalidBadgeIdInvalidChars))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 401 response when authorised by auth API but badge identifier exists but contains all lowercase chars" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(validatedHeadersRequestWithInvalidBadgeIdLowerCase))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 401 response when authorised by auth API where badge identifier exists and eori does not" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(TestValidatedHeadersRequestWithBadgeIdAndNoEori))

        actual shouldBe Left(errorResponseEoriIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 401 response when authorised by auth API empty eori" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(validatedHeadersRequestWithValidBadgeIdAndEmptyEori))

        actual shouldBe Left(errorResponseEoriIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }
      
      "Return 401 response when authorised by auth API with eori too long" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(validatedHeadersRequestWithValidBadgeIdAndEoriTooLong))

        actual shouldBe Left(errorResponseEoriIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 401 response when authorised by auth API with eori containing invalid characters" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(validatedHeadersRequestWithInvalidEoriInvalidChars))

        actual shouldBe Left(errorResponseEoriIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 401 response when authorised by auth API but both badge identifier and eori are invalid" in new SetUp {
        authoriseCsp()

        private val actual = await(fileUploadAuthAction.refine(validatedHeadersRequestWithInvalidBadgeIdEoriPair))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 500 response if errors occur in CSP auth API call" in new SetUp {
        authoriseCspError()

        private val actual = await(fileUploadAuthAction.refine(TestValidatedHeadersRequestNoBadge))

        actual shouldBe Left(ErrorInternalServerError.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }
    }
  }
}
