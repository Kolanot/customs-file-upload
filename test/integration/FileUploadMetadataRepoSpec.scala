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

package integration

import java.net.URL

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsXml
import play.api.test.Helpers
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.{HasConversationId, ValidatedHeadersRequest}
import uk.gov.hmrc.customs.file.upload.model.{CallbackFields, FileUploadConfig}
import uk.gov.hmrc.customs.file.upload.repo.{FileUploadMetadataMongoRepo, FileUploadMetadataRepoErrorHandler}
import uk.gov.hmrc.customs.file.upload.services.FileUploadConfigService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import util.ApiSubscriptionFieldsTestData.subscriptionFieldsId
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.TestData.{FileMetadataWithFileOne, _}

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

class FileUploadMetadataRepoSpec extends IntegrationTestSpec
  with MockitoSugar
  with MongoSpecSupport {

  implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
  
  trait SetUp {
    val mockLogger: FileUploadLogger = mock[FileUploadLogger]
    val mockErrorHandler: FileUploadMetadataRepoErrorHandler = mock[FileUploadMetadataRepoErrorHandler]
    val mockFileUploadConfigService: FileUploadConfigService = mock[FileUploadConfigService]
    val mockFileUploadConfig: FileUploadConfig = mock[FileUploadConfig]
    lazy implicit val emptyHC: HeaderCarrier = HeaderCarrier()
    implicit val implicitVHR: ValidatedHeadersRequest[AnyContentAsXml] = TestValidatedHeadersRequest
    when(mockFileUploadConfigService.fileUploadConfig).thenReturn(mockFileUploadConfig)
    when(mockFileUploadConfig.ttlInSeconds).thenReturn(10000)
    val repository = new FileUploadMetadataMongoRepo(reactiveMongoComponent, mockErrorHandler, mockFileUploadConfigService, mockLogger)
  }

  override implicit lazy val mongoConnectorForTest: MongoConnector = MongoConnector(mongoUri)
  val reactiveMongoComponent: ReactiveMongoComponent =
    new ReactiveMongoComponent {
      override def mongoConnector: MongoConnector = mongoConnectorForTest
    }

  override def beforeEach() {
    dropTestCollection("file-upload-metadata")
  }

  override def afterAll() {
    dropTestCollection("file-upload-metadata")
  }

  private def collectionSize(repository: FileUploadMetadataMongoRepo): Int = {
    await(repository.count(Json.obj()))
  }

  private def logVerifier(mockLogger: FileUploadLogger, logLevel: String, logText: String): Unit = {
    PassByNameVerifier(mockLogger, logLevel)
      .withByNameParam(logText)
      .withParamMatcher(any[HasConversationId])
      .verify()
  }

  private def selector(fileReference: String) = {
    "files.reference" -> play.api.libs.json.Json.toJsFieldJsValueWrapper(fileReference)
  }

  "repository" should {
    "successfully save a single file metadata" in new SetUp {
      when(mockErrorHandler.handleSaveError(any(), any())(any())).thenReturn(true)
      when(mockFileUploadConfigService.fileUploadConfig).thenReturn(mockFileUploadConfig)
      when(mockFileUploadConfig.ttlInSeconds).thenReturn(10000)
      val saveResult = await(repository.create(FileMetadataWithFileOne))
      saveResult shouldBe true
      collectionSize(repository) shouldBe 1

      val findResult = await(repository.find(selector(BatchFileOne.reference.toString)).head)

      findResult shouldBe FileMetadataWithFileOne
      logVerifier(mockLogger, "debug", "saving fileUploadMetadata: FileUploadMetadata(1,123,327d9145-4965-4d28-a2c5-39dedee50334,48400000-8cf0-11bd-b23e-10b96e4ef001,1,2018-04-24T09:30:00.000Z,List(BatchFile(31400000-8ce0-11bd-b23e-10b96e4ef00f,Some(CallbackFields(name1,application/xml,checksum1,2018-04-24T09:30:00Z,https://outbound.a.com)),https://a.b.com,1,1,Some(Document Type 1))))")
    }

    "successfully save when create is called multiple times" in new SetUp {
      await(repository.create(FileMetadataWithFileOne))
      await(repository.create(FileMetadataWithFileTwo))
      collectionSize(repository) shouldBe 2

      val findResult1 = await(repository.find(selector(BatchFileOne.reference.toString)).head)

      findResult1 shouldBe FileMetadataWithFileOne

      val findResult2 = await(repository.find(selector(BatchFileTwo.reference.toString)).head)

      findResult2 shouldBe FileMetadataWithFileTwo
    }

    "successfully update checksum, searching by reference" in new SetUp {
      await(repository.create(FileMetadataWithFilesOneAndThree))
      await(repository.create(FileMetadataWithFileTwo))
      val updatedFileOne = BatchFileOne.copy(maybeCallbackFields = Some(CallbackFields("UPDATED_NAME", "UPDATED_MIMETYPE", "UPDATED_CHECKSUM", InitiateDate, new URL("https://outbound.a.com"))))
      val expectedRecord = FileMetadataWithFilesOneAndThree.copy(files = Seq(updatedFileOne, BatchFileThree))

      val maybeActual = await(repository.update(subscriptionFieldsId, FileReferenceOne, CallbackFieldsUpdated))

      maybeActual shouldBe Some(expectedRecord)
      await(repository.fetch(BatchFileOne.reference)) shouldBe Some(expectedRecord)
      await(repository.fetch(BatchFileTwo.reference)) shouldBe Some(FileMetadataWithFileTwo)
      logVerifier(mockLogger, "debug", "updating file upload metadata with file reference: 31400000-8ce0-11bd-b23e-10b96e4ef00f with callbackField=CallbackFields(UPDATED_NAME,UPDATED_MIMETYPE,UPDATED_CHECKSUM,2018-04-24T09:30:00Z,https://outbound.a.com)")
    }

    "not update checksum, when searching by reference fails" in new SetUp {
      await(repository.create(FileMetadataWithFileTwo))

      val maybeActual = await(repository.update(subscriptionFieldsId, FileReferenceOne, CallbackFieldsUpdated))

      maybeActual shouldBe None
    }

    "return Some when fetch by file reference is successful" in new SetUp {
      await(repository.create(FileMetadataWithFileOne))
      await(repository.create(FileMetadataWithFileTwo))

      val maybeFoundRecordOne = await(repository.fetch(BatchFileOne.reference))

      maybeFoundRecordOne shouldBe Some(FileMetadataWithFileOne)

      val maybeFoundRecordTwo = await(repository.fetch(BatchFileTwo.reference))

      maybeFoundRecordTwo shouldBe Some(FileMetadataWithFileTwo)
      logVerifier(mockLogger, "debug", "fetching file upload metadata with file reference: 31400000-8ce0-11bd-b23e-10b96e4ef00f")
    }

    "return None when fetch by file reference is un-successful" in new SetUp {
      await(repository.create(FileMetadataWithFileTwo))

      val maybeFoundRecord = await(repository.fetch(BatchFileOne.reference))

      maybeFoundRecord shouldBe None
    }

    "successfully delete all file upload metadata" in new SetUp {
      await(repository.create(FileMetadataWithFileOne))
      await(repository.create(FileMetadataWithFileTwo))
      collectionSize(repository) shouldBe 2

      await(repository.deleteAll())

      collectionSize(repository) shouldBe 0
    }
  }
}
