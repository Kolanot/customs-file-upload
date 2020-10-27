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

package uk.gov.hmrc.customs.file.upload.connectors

import com.google.inject._
import play.api.libs.json.Json
import play.mvc.Http.HeaderNames._
import play.mvc.Http.MimeTypes.JSON
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.FileTransmission
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.HasConversationId
import uk.gov.hmrc.customs.file.upload.services.FileUploadConfigService
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, HttpResponse}
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileTransmissionConnector @Inject()(http: HttpClient,
                                          config: FileUploadConfigService,
                                          logger: FileUploadLogger)
                                         (implicit ec: ExecutionContext) {

  private implicit val hc: HeaderCarrier = HeaderCarrier(
    extraHeaders = Seq(ACCEPT -> JSON, CONTENT_TYPE -> JSON, USER_AGENT -> "customs-file-upload")
  )

  def send[A](request: FileTransmission)(implicit hasConversationId: HasConversationId): Future[Unit] = {
    post(request, config.fileUploadConfig.fileTransmissionBaseUrl)
  }

  private def post[A](request: FileTransmission, url: String)(implicit hasConversationId: HasConversationId): Future[Unit] = {
    logger.debug(s"Sending request to file transmission service. Url: $url Payload: ${Json.prettyPrint(Json.toJson(request))}")
    http.POST[FileTransmission, HttpResponse](url, request).map{ _ =>
      logger.info(s"[conversationId=${request.file.reference}]: file transmission request sent successfully")
      ()
    }.recoverWith {
        case httpError: HttpException =>
          val msg = s"Call to file transmission failed. url=$url, HttpStatus=${httpError.responseCode}, Error=${httpError.message}"
          logger.debug(msg, httpError)
          logger.error(msg)
          Future.failed(new RuntimeException(httpError))
        case e: Throwable =>
          logger.error(s"Call to file transmission failed. url=$url")
          Future.failed(e)
      }
  }

}
