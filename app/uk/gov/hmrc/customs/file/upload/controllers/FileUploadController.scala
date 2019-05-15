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

package uk.gov.hmrc.customs.file.upload.controllers

import javax.inject.{Inject, Singleton}
import play.api.http.ContentTypes
import play.api.mvc._
import uk.gov.hmrc.customs.file.upload.controllers.actionBuilders.{AuthAction, ConversationIdAction, PayloadContentValidationAction, PayloadValidationAction, ValidateAndExtractHeadersAction}
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.ValidatedFileUploadPayloadRequest
import uk.gov.hmrc.customs.file.upload.services.FileUploadBusinessService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext

@Singleton
class FileUploadController @Inject()(val conversationIdAction: ConversationIdAction,
                                     val validateAndExtractHeadersAction: ValidateAndExtractHeadersAction,
                                     val authAction: AuthAction,
                                     val payloadValidationAction: PayloadValidationAction,
                                     val payloadContentValidationAction: PayloadContentValidationAction,
                                     val fileUploadBusinessService: FileUploadBusinessService,
                                     val logger: FileUploadLogger)
                                    (implicit ec: ExecutionContext)
  extends BaseController {

  private def xmlOrEmptyBody: BodyParser[AnyContent] = BodyParser(rq => parse.xml(rq).map {
    case Right(xml) =>
      Right(AnyContentAsXml(xml))
    case _ =>
      Right(AnyContentAsEmpty)
  })

  def post(): Action[AnyContent] = (
    Action andThen
      conversationIdAction andThen
      validateAndExtractHeadersAction andThen
      authAction andThen
      payloadValidationAction andThen
      payloadContentValidationAction
    ).async(bodyParser = xmlOrEmptyBody) {

    implicit validatedRequest: ValidatedFileUploadPayloadRequest[AnyContent] =>

      logger.debug(s"File upload initiate request received. Payload=${validatedRequest.body.toString} headers=${validatedRequest.headers.headers}")

      fileUploadBusinessService.send map {
        case Right(res) =>
          logger.info("Upload initiate request processed successfully")
          Ok(res).withConversationId.as(ContentTypes.XML)
        case Left(errorResult) =>
          errorResult
      }
  }
}
