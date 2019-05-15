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

package uk.gov.hmrc.customs.file.upload.services

import javax.inject.{Inject, Singleton}
import play.api.http.Status
import play.api.http.Status.UNAUTHORIZED
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{ErrorInternalServerError, UnauthorizedCode}
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.HasConversationId
import uk.gov.hmrc.customs.file.upload.model.{Eori, NonCsp}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.Authorization

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Left
import scala.util.control.NonFatal

@Singleton
class CustomsAuthService @Inject()(override val authConnector: AuthConnector,
                                   logger: FileUploadLogger)
                                  (implicit ec: ExecutionContext) extends AuthorisedFunctions {

  private val hmrcCustomsEnrolment = "HMRC-CUS-ORG"

  private lazy val errorResponseEoriNotFoundInCustomsEnrolment =
    ErrorResponse(UNAUTHORIZED, UnauthorizedCode, "EORI number not found in Customs Enrolment")
  private val errorResponseUnauthorisedGeneral =
    ErrorResponse(Status.UNAUTHORIZED, UnauthorizedCode, "Unauthorised request")

  type IsCsp = Boolean

  /*
  Wrapper around HMRC authentication library authorised function for CSP authentication
  */
  def authAsCsp()(implicit vhr: HasConversationId, hc: HeaderCarrier): Future[Either[ErrorResponse, IsCsp]] = {
    val eventualAuth =
      authorised(Enrolment("write:customs-file-upload") and AuthProviders(PrivilegedApplication)) {
        Future.successful[Either[ErrorResponse, IsCsp]] {
          logger.debug("authorised as CSP")
          Right(true)
        }
      }

    eventualAuth.recover{
      case NonFatal(_: AuthorisationException) =>
        logger.debug("Not authorised as CSP")
        Right(false)
      case NonFatal(e) =>
        logger.error("Error authorising CSP", e)
        Left(ErrorInternalServerError)
    }
  }

  /*
    Wrapper around HMRC authentication library authorised function for NON CSP authentication
    */
  def authAsNonCsp()(implicit vhr: HasConversationId, hc: HeaderCarrier): Future[Either[ErrorResponse, NonCsp]] = {
    val eventualAuth: Future[Enrolments] =
      authorised(Enrolment(hmrcCustomsEnrolment) and AuthProviders(GovernmentGateway)).retrieve(Retrievals.authorisedEnrolments) {
        logger.debug("authorised as non-CSP and requested authorisedEnrolment retrievals")
        enrolments =>
          Future.successful(enrolments)
      }

    eventualAuth.map{ enrolmentsData =>
      val enrolments: Enrolments = enrolmentsData
      val maybeEori: Option[Eori] = findEoriInCustomsEnrolment(enrolments, hc.authorization)
      logger.debug(s"EORI from Customs enrolment for non-CSP request: $maybeEori")
      maybeEori.fold[Either[ErrorResponse, NonCsp]]{
        Left(errorResponseEoriNotFoundInCustomsEnrolment)
      }{ eori =>
        logger.debug("Authorising as non-CSP")
        Right(NonCsp(eori))
      }
    }.recover{
      case NonFatal(_: AuthorisationException) =>
        Left(errorResponseUnauthorisedGeneral)
      case NonFatal(e) =>
        logger.error("Error authorising non-CSP", e)
        Left(ErrorInternalServerError)
    }
  }

  private def findEoriInCustomsEnrolment[A](enrolments: Enrolments, authHeader: Option[Authorization])(implicit vhr: HasConversationId, hc: HeaderCarrier): Option[Eori] = {
    val maybeCustomsEnrolment = enrolments.getEnrolment(hmrcCustomsEnrolment)
    if (maybeCustomsEnrolment.isEmpty) {
      logger.warn(s"Customs enrolment $hmrcCustomsEnrolment not retrieved for authorised non-CSP call")
    }
    for {
      customsEnrolment <- maybeCustomsEnrolment
      eoriIdentifier <- customsEnrolment.getIdentifier("EORINumber")
    } yield Eori(eoriIdentifier.value)
  }

}
