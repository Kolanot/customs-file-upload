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

package uk.gov.hmrc.customs.file.upload.connectors

import com.google.inject.{Inject, Singleton}
import play.mvc.Http.HeaderNames._
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.customs.file.upload.controllers.CustomHeaderNames.{XCdsClientIdHeaderName, XConversationIdHeaderName}
import uk.gov.hmrc.customs.file.upload.http.Non2xxResponseException
import uk.gov.hmrc.customs.file.upload.services.{FileUploadConfigService, FileUploadCustomsNotification}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CustomsNotificationConnector @Inject()(http: HttpClient,
                                             logger: CdsLogger,
                                             config: FileUploadConfigService)
                                            (implicit ec: ExecutionContext) extends HttpErrorFunctions {

  private implicit val hc = HeaderCarrier()
  private val XMLHeader = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

  def send(notification: FileUploadCustomsNotification): Future[Unit] = {

    val headers: Map[String, String] = Map(
      XCdsClientIdHeaderName -> notification.clientSubscriptionId.toString,
      XConversationIdHeaderName -> notification.conversationId.toString,
      CONTENT_TYPE -> s"${MimeTypes.XML}; charset=UTF-8",
      ACCEPT -> MimeTypes.XML,
      AUTHORIZATION -> s"Basic ${config.fileUploadConfig.customsNotificationBearerToken}")

    val url = config.fileUploadConfig.customsNotificationBaseUrl

    http.POSTString[HttpResponse](url, XMLHeader + notification.payload.toString(), headers.toSeq).map { response =>
      response.status match {
        case status if is2xx(status) =>
          logger.info(s"[conversationId=${notification.conversationId}][clientSubscriptionId=${notification.clientSubscriptionId}]: notification sent successfully. url=${config.fileUploadConfig.customsNotificationBaseUrl}")
          ()

        case status => //1xx, 3xx, 4xx, 5xx
          throw new Non2xxResponseException(status)
      }
    }.recoverWith {
      case httpError: HttpException =>
        logger.error(s"Call to customs notification service failed. url=$url, HttpStatus=${httpError.responseCode}, Error=${httpError.message}")
        Future.failed(new RuntimeException(httpError))

      case e: Throwable =>
        logger.error(s"[conversationId=${notification.conversationId}][clientSubscriptionId=${notification.clientSubscriptionId}]: Call to customs notification failed. url=${config.fileUploadConfig.customsNotificationBaseUrl}")
        Future.failed(e)
    }
  }
}
