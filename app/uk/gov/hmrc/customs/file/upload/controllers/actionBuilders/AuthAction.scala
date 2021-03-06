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

package uk.gov.hmrc.customs.file.upload.controllers.actionBuilders

import javax.inject.{Inject, Singleton}
import play.api.mvc.{ActionRefiner, RequestHeader, Result}
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.{AuthorisedRequest, HasConversationId, HasRequest, ValidatedHeadersRequest}
import uk.gov.hmrc.customs.file.upload.model.{AuthorisedAsCsp, BadgeIdentifier, CspWithEori, Eori}
import uk.gov.hmrc.customs.file.upload.services.{CustomsAuthService, FileUploadConfigService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Left


/** Action builder that attempts to authorise request as a CSP or else NON CSP
  * <ul>
  * <li/>INPUT - `ValidatedHeadersRequest`
  * <li/>OUTPUT - `AuthorisedRequest` - authorised will be `AuthorisedAs.Csp` or `AuthorisedAs.NonCsp`
  * <li/>ERROR -
  * <ul>
  * <li/>401 if authorised as CSP but badge identifier not present for CSP
  * <li/>401 if authorised as NON CSP but enrolments does not contain an EORI.
  * <li/>401 if not authorised as CSP or NON CSP
  * <li/>500 on any downstream errors a 500 is returned
  * </ul>
  * </ul>
  */
@Singleton
class AuthAction @Inject()(customsAuthService: CustomsAuthService,
                           headerValidator: HeaderValidator,
                           logger: FileUploadLogger,
                           fileUploadConfigService: FileUploadConfigService)
                          (implicit ec: ExecutionContext)
  extends ActionRefiner[ValidatedHeadersRequest, AuthorisedRequest] {

  override def refine[A](vhr: ValidatedHeadersRequest[A]): Future[Either[Result, AuthorisedRequest[A]]] = {
    implicit val implicitVhr: ValidatedHeadersRequest[A] = vhr
    implicit def hc(implicit rh: RequestHeader): HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(rh.headers)

    authAsCspWithMandatoryAuthHeaders().flatMap{
      case Right(maybeAuthorisedAsCspWithBadgeIdentifier) =>
        maybeAuthorisedAsCspWithBadgeIdentifier.fold{
          customsAuthService.authAsNonCsp().map[Either[Result, AuthorisedRequest[A]]]{
            case Left(errorResponse) =>
              Left(errorResponse.XmlResult.withConversationId)
            case Right(nonCspData) =>
              Right(vhr.toNonCspAuthorisedRequest(nonCspData.eori))
          }
        }{ cspData =>
          Future.successful(Right(vhr.toCspAuthorisedRequest(cspData)))
        }
      case Left(result) =>
        Future.successful(Left(result.XmlResult.withConversationId))
    }
  }

  private def authAsCspWithMandatoryAuthHeaders[A]()(implicit vhr: HasRequest[A] with HasConversationId, hc: HeaderCarrier): Future[Either[ErrorResponse, Option[AuthorisedAsCsp]]] = {

    val eventualAuthWithBadgeId: Future[Either[ErrorResponse, Option[AuthorisedAsCsp]]] = customsAuthService.authAsCsp().map{
      case Right(isCsp) =>
        if (isCsp) {
          eitherAuthorisedAsCsp().right.map(authAsCsp => Some(authAsCsp))
        } else {
          Right(None)
        }
      case Left(errorResponse) =>
        Left(errorResponse)
    }

    eventualAuthWithBadgeId
  }

  protected def eitherAuthorisedAsCsp[A]()(implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, AuthorisedAsCsp] = {

    for {
      badgeId <- eitherBadgeIdentifier.right
      eori <- eitherEori.right
    } yield CspWithEori(badgeId, eori)
  }

  protected def eitherBadgeIdentifier[A](implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, BadgeIdentifier] = {
    headerValidator.eitherBadgeIdentifier
  }

  private def eitherEori[A](implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, Eori] = {
    headerValidator.eoriMustBeValidAndPresent()
  }

  override protected def executionContext: ExecutionContext = ec
}
