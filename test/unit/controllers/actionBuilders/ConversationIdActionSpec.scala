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

package unit.controllers.actionBuilders

import org.scalatestplus.mockito.MockitoSugar
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.customs.file.upload.controllers.actionBuilders.ConversationIdAction
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.ConversationIdRequest
import uk.gov.hmrc.customs.file.upload.services.DateTimeService
import util.TestData.{conversationId, stubUniqueIdsService}
import util.UnitSpec

class ConversationIdActionSpec extends UnitSpec with MockitoSugar {

  trait SetUp {
    private implicit val ec = Helpers.stubControllerComponents().executionContext
    private val mockFileUploadLogger = mock[FileUploadLogger]
    protected val mockDateTimeService: DateTimeService = mock[DateTimeService]

    val request = FakeRequest()
    val conversationIdAction = new ConversationIdAction(stubUniqueIdsService, mockDateTimeService, mockFileUploadLogger)
    val expected = ConversationIdRequest(conversationId, request)
  }

  "ConversationIdAction" should {
    "Generate a Request containing a unique correlation id" in new SetUp {

      private val actual = await(conversationIdAction.transform(request))

      actual shouldBe expected
    }
  }

}
