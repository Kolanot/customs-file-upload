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

import org.mockito.ArgumentMatchers.any
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames._
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse._
import uk.gov.hmrc.customs.file.upload.controllers.CustomHeaderNames.{XEoriIdentifierHeaderName, _}
import uk.gov.hmrc.customs.file.upload.controllers.actionBuilders.HeaderValidator
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.{ConversationIdRequest, ExtractedHeaders, ExtractedHeadersImpl, HasConversationId}
import uk.gov.hmrc.customs.file.upload.model.{Eori, VersionOne}
import uk.gov.hmrc.play.test.UnitSpec
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.RequestHeaders._
import util.{ApiSubscriptionFieldsTestData, TestData}

class HeaderValidatorSpec extends UnitSpec with TableDrivenPropertyChecks with MockitoSugar {

  private val extractedHeadersWithBadgeIdentifierV1 = ExtractedHeadersImpl(VersionOne, ApiSubscriptionFieldsTestData.clientId)

  trait SetUp {
    val mockLogger: FileUploadLogger = mock[FileUploadLogger]
    val validator = new HeaderValidator(mockLogger)

    def validate(c: ConversationIdRequest[_]): Either[ErrorResponse, ExtractedHeaders] = {
      validator.validateHeaders(c)
    }
  }

  "HeaderValidator" can {
    "in happy path, validation" should {
      "be successful for a valid request with accept header for V1" in new SetUp {
        validate(conversationIdRequest(ValidHeadersV1)) shouldBe Right(extractedHeadersWithBadgeIdentifierV1)
      }
      "be successful for content type XML with no space header" in new SetUp {
        validate(conversationIdRequest(ValidHeadersV1 + (CONTENT_TYPE -> "application/xml;charset=utf-8"))) shouldBe Right(extractedHeadersWithBadgeIdentifierV1)
      }
    }
    "in unhappy path, validation" should {
      "fail when request is missing accept header" in new SetUp {
        validate(conversationIdRequest(ValidHeadersV1 - ACCEPT)) shouldBe Left(ErrorAcceptHeaderInvalid)
      }
      "fail when request is missing content type header" in new SetUp {
        validate(conversationIdRequest(ValidHeadersV1 - CONTENT_TYPE)) shouldBe Left(ErrorContentTypeHeaderInvalid)
      }
      "fail when request is missing X-Client-ID header" in new SetUp {
        validate(conversationIdRequest(ValidHeadersV1 - XClientIdHeaderName)) shouldBe Left(ErrorInternalServerError)
      }
      "fail when request has invalid accept header" in new SetUp {
        validate(conversationIdRequest(ValidHeadersV1 + ACCEPT_HEADER_INVALID)) shouldBe Left(ErrorAcceptHeaderInvalid)
      }
      "fail when request has invalid content type header (for JSON)" in new SetUp {
        validate(conversationIdRequest(ValidHeadersV1 + CONTENT_TYPE_HEADER_INVALID)) shouldBe Left(ErrorContentTypeHeaderInvalid)
      }
      "fail when request has invalid X-Client-ID header" in new SetUp {
        validate(conversationIdRequest(ValidHeadersV1 + X_CLIENT_ID_HEADER_INVALID)) shouldBe Left(ErrorInternalServerError)
      }
    }

    "in validating the badge identifier header" should {
      "log at info level" in new SetUp {

        validator.eitherBadgeIdentifier(conversationIdRequest(ValidHeadersV1))

        PassByNameVerifier(mockLogger, "info")
          .withByNameParam[String]("X-Badge-Identifier header passed validation: BADGEID123")
          .withParamMatcher[HasConversationId](any[HasConversationId])
          .verify()
      }
    }

    "in validating the eori header" should {
      "not allow an empty header" in new SetUp {
        private val value = validator.eoriMustBeValidAndPresent()(conversationIdRequest(ValidHeadersV1 + (XEoriIdentifierHeaderName -> "")))

        value shouldBe Left(errorBadRequest(s"$XEoriIdentifierHeaderName header is missing or invalid"))
      }

      "not allow only spaces in the header" in new SetUp {
        private val value = validator.eoriMustBeValidAndPresent()(conversationIdRequest(ValidHeadersV1 + (XEoriIdentifierHeaderName -> "       ")))

        value shouldBe Left(errorBadRequest(s"$XEoriIdentifierHeaderName header is missing or invalid"))
      }

      "now allow headers longer than 17 characters" in new SetUp {
        private val value = validator.eoriMustBeValidAndPresent()(conversationIdRequest(ValidHeadersV1 + (XEoriIdentifierHeaderName -> "012345678901234567")))

        value shouldBe Left(errorBadRequest(s"$XEoriIdentifierHeaderName header is missing or invalid"))
      }

      "allow headers with leading spaces" in new SetUp {
        private val value = validator.eoriMustBeValidAndPresent()(conversationIdRequest(ValidHeadersV1 + (XEoriIdentifierHeaderName -> "  0123456789")))

        value shouldBe Right(Eori("  0123456789"))
      }

      "allow headers with trailing spaces" in new SetUp {
        private val value = validator.eoriMustBeValidAndPresent()(conversationIdRequest(ValidHeadersV1 + (XEoriIdentifierHeaderName -> "0123456789    ")))

        value shouldBe Right(Eori("0123456789    "))
      }

      "allow headers with embedded spaces" in new SetUp {
        private val value = validator.eoriMustBeValidAndPresent()(conversationIdRequest(ValidHeadersV1 + (XEoriIdentifierHeaderName -> "01234  56789")))

        value shouldBe Right(Eori("01234  56789"))
      }

      "allow special characters" in new SetUp {
        private val value = validator.eoriMustBeValidAndPresent()(conversationIdRequest(ValidHeadersV1 + (XEoriIdentifierHeaderName -> "!£$%^&*()-_=+/<>@")))

        value shouldBe Right(Eori("!£$%^&*()-_=+/<>@"))
      }

      "log info level when valid" in new SetUp {
        validator.eoriMustBeValidAndPresent()(conversationIdRequest(ValidHeadersV1 + (XEoriIdentifierHeaderName -> "ABCABC")))

        PassByNameVerifier(mockLogger, "info")
          .withByNameParam[String]("X-EORI-Identifier header passed validation: ABCABC")
          .withParamMatcher[HasConversationId](any[HasConversationId])
          .verify()
      }
    }
  }

  private def conversationIdRequest(requestMap: Map[String, String]): ConversationIdRequest[_] =
    ConversationIdRequest(TestData.conversationId, FakeRequest().withHeaders(requestMap.toSeq: _*))
}
