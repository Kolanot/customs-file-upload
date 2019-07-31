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

package component

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, OptionValues}
import play.api.libs.json.{JsObject, JsString}
import play.api.mvc._
import play.api.mvc.request.RequestTarget
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.customs.file.upload.model.{ApiSubscriptionKey, VersionOne}
import util.FakeRequests._
import util.RequestHeaders.X_CONVERSATION_ID_NAME
import util.TestData._
import util.XmlOps.stringToXml
import util.externalservices.{ApiSubscriptionFieldsService, AuthService, UpscanInitiateService}
import util.{AuditService, TestData}

import scala.concurrent.{Await, Future}

class FileUploadSpec extends ComponentTestSpec
  with Matchers
  with OptionValues
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with UpscanInitiateService
  with ApiSubscriptionFieldsService
  with AuthService
  with AuditService {

  private val endpoint = "/upload"
  
  private val apiSubscriptionKeyForXClientIdV1 =
    ApiSubscriptionKey(clientId = clientId, context = "customs%2Fsupporting-documentation", version = VersionOne)

  override protected def beforeAll() {
    startMockServer()
  }

  override protected def beforeEach() {
    resetMockServer()
  }

  override protected def afterAll() {
    stopMockServer()
  }

  private val MalformedXmlBodyError: String  =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>BAD_REQUEST</code>
      |  <message>Request body does not contain a well-formed XML document.</message>
      |</errorResponse>
    """.stripMargin

  private val UnauthorisedRequestError: String =
    """<errorResponse>
      |  <code>UNAUTHORIZED</code>
      |  <message>Unauthorised request</message>
      |</errorResponse>
    """.stripMargin
 
   feature("Valid request is processed correctly") {
    scenario("Response status 200 when user submits correct request") {
      Given("the API is available")
      startApiSubscriptionFieldsService(apiSubscriptionKeyForXClientIdV1)
      val request = ValidFileUploadV1Request.fromNonCsp.postTo(endpoint)
      setupExternalServiceExpectations()

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)
      val resultFuture = result.value

      Then("a response with a 200 (OK) status is received")
      status(resultFuture) shouldBe OK

      headers(resultFuture).get(X_CONVERSATION_ID_NAME) shouldBe 'defined
    }
  }

  feature("Unauthorized file upload API submissions are processed correctly") {

    scenario("An unauthorised CSP is not allowed to submit a file upload request with v1.0 accept header") {
      Given("A CSP wants to submit a valid file upload")
      val request: FakeRequest[AnyContentAsXml] = InvalidFileUploadRequest.fromCsp.postTo(endpoint)

      And("the CSP is unauthorised with its privileged application")
      authServiceUnauthorisesScopeForCSP()
      authServiceUnauthorisesCustomsEnrolmentForNonCSP(cspBearerToken)

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)
      val resultFuture = result.value

      Then("a response with a 401 (UNAUTHORIZED) status is received")
      status(resultFuture) shouldBe UNAUTHORIZED

      And("the response body is empty")
      stringToXml(contentAsString(resultFuture)) shouldBe stringToXml(UnauthorisedRequestError)

      And("the request was authorised with AuthService")
      eventually(verifyAuthServiceCalledForCsp())
    }

  }

  feature("The API handles errors as expected") {
    scenario("Response status 400 when user submits a non-xml payload") {
      Given("the API is available")
      val request = InvalidFileUploadRequest.fromNonCsp
        .withJsonBody(JsObject(Seq("something" -> JsString("I am a json"))))
        .withMethod("POST")
        .withTarget(RequestTarget(path = endpoint, uriString = InvalidFileUploadRequest.uri, queryString = InvalidFileUploadRequest.queryString))
      setupExternalServiceExpectations()

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 400 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe BAD_REQUEST
      headers(resultFuture).get(X_CONVERSATION_ID_NAME) shouldBe 'defined

      And("the response body is a \"malformed xml body\" XML")
      stringToXml(contentAsString(resultFuture)) shouldBe stringToXml(MalformedXmlBodyError)
    }

    scenario("Response status 400 when user submits a malformed xml payload") {
      Given("the API is available")
      val request = MalformedXmlRequest.fromNonCsp.withMethod("POST")
        .withTarget(RequestTarget(path = endpoint, uriString = MalformedXmlRequest.uri, queryString = MalformedXmlRequest.queryString))
      setupExternalServiceExpectations()

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 400 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe BAD_REQUEST
      headers(resultFuture).get(X_CONVERSATION_ID_NAME) shouldBe 'defined

      And("the response body is a \"malformed xml body\" XML")
      stringToXml(contentAsString(resultFuture)) shouldBe stringToXml(MalformedXmlBodyError)
    }
  }

  private def setupExternalServiceExpectations(): Unit = {
    stubAuditService()
    authServiceUnauthorisesScopeForCSP(TestData.nonCspBearerToken)
    authServiceAuthorizesNonCspWithEori()
    startUpscanInitiateService()
  }
}
