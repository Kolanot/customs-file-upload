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

package unit.controllers

import controllers.Assets
import org.mockito.Mock
import org.mockito.Mockito.reset
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.http.HttpErrorHandler
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import uk.gov.hmrc.customs.file.upload.controllers.FileUploadDocumentationController
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger

class FileUploadDocumentationControllerSpec extends PlaySpec with MockitoSugar with Results with BeforeAndAfterEach {

  private val assets = mock[Assets]
  
  private val cc = Helpers.stubControllerComponents()

  private val mockLogger = mock[FileUploadLogger]

  private val v1WhitelistedAppIdsConfigs = Map(
    "api.access.version-1.0.whitelistedApplicationIds.0" -> "v1AppId-1",
    "api.access.version-1.0.whitelistedApplicationIds.1" -> "v1AppId-2")

  private def getApiDefinitionWith(configMap: Map[String, Any]) =
    new FileUploadDocumentationController(assets, cc, play.api.Configuration.from(configMap), mockLogger)
      .definition()

  override def beforeEach() {
    reset(assets)
  }

  "API Definition" should {

    "be correct when V1 is PUBLIC by default" in {
      val result = getApiDefinitionWith(Map())(FakeRequest())

      status(result) mustBe 200
      contentAsJson(result) mustBe expectedJson(None)
    }

    "be correct when V1 is PRIVATE" in {
      val result = getApiDefinitionWith(v1WhitelistedAppIdsConfigs)(FakeRequest())

      status(result) mustBe 200
      contentAsJson(result) mustBe expectedJson(expectedV1WhitelistedAppIds = Some(v1WhitelistedAppIdsConfigs.values))
    }

  }

  private def expectedJson(expectedV1WhitelistedAppIds: Option[Iterable[String]]) =
    Json.parse(
      s"""
         |{
         |   "scopes":[
         |      {
         |         "key":"write:customs-file-upload",
         |         "name":"Request permission to upload file(s)",
         |         "description":"Request permission to upload file(s)"
         |      }
         |   ],
         |   "api":{
         |      "name":"Document Submission API",
         |      "description":"Allows traders to submit supporting documents for their declarations",
         |      "categories": ["CUSTOMS"],
         |      "context":"customs/supporting-documentation",
         |      "versions":[
         |         {
         |            "version":"1.0",
         |            "status":"BETA",
         |            "endpointsEnabled":true,
         |            "access":{
         |               """.stripMargin
        +
        expectedV1WhitelistedAppIds.fold(""" "type":"PUBLIC" """)(ids =>
          """ "type":"PRIVATE", "whitelistedApplicationIds":[ """.stripMargin
            + ids.map(x => s""" "$x" """).mkString(",") + "]"
        )
        + """
          |          },
          |            "fieldDefinitions":[
          |               {
          |                  "name":"callbackUrl",
          |                  "description":"What's your callback URL for receiving notifications?",
          |                  "type":"URL",
          |                  "hint":"This is how we'll notify you when we've completed processing. It must include https and port 443."
          |               },
          |               {
          |                  "name":"securityToken",
          |                  "description":"What's the value of the HTTP Authorization header we should use to notify you?",
          |                  "type":"SecureToken",
          |                  "hint":"For example: Basic YXNkZnNhZGZzYWRmOlZLdDVOMVhk"
          |               }
          |            ]
          |         }
          |     ]
          |  }
          |}""".stripMargin)

}
