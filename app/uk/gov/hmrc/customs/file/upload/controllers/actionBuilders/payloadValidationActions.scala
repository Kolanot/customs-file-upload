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

package uk.gov.hmrc.customs.file.upload.controllers.actionBuilders

import javax.inject.{Inject, Singleton}
import play.api.http.Status
import play.api.mvc.{ActionRefiner, AnyContent, Result}
import play.mvc.Http.Status.FORBIDDEN
import uk.gov.hmrc.customs.api.common.controllers.{ErrorResponse, HttpStatusCodeShortDescriptions, ResponseContents}
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.file.upload.model.actionbuilders._
import uk.gov.hmrc.customs.file.upload.model._
import uk.gov.hmrc.customs.file.upload.services.{FileUploadConfigService, XmlValidationService}

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.xml.{Node, NodeSeq, SAXException}

@Singleton
class PayloadValidationAction @Inject()(xmlValidationService: XmlValidationService,
                                        logger: FileUploadLogger)
                                       (implicit ec: ExecutionContext)
  extends ActionRefiner[AuthorisedRequest, ValidatedPayloadRequest] {

  override def refine[A](ar: AuthorisedRequest[A]): Future[Either[Result, ValidatedPayloadRequest[A]]] = {
    implicit val implicitAr: AuthorisedRequest[A] = ar

    validateXml
  }

  private def validateXml[A](implicit ar: AuthorisedRequest[A]): Future[Either[Result, ValidatedPayloadRequest[A]]] = {
    lazy val errorMessage = "Request body does not contain a well-formed XML document."
    lazy val errorNotWellFormed = ErrorResponse.errorBadRequest(errorMessage).XmlResult.withConversationId

    def validate(xml: NodeSeq): Future[Either[Result, ValidatedPayloadRequest[A]]] =
      xmlValidationService.validate(xml).map{ _ =>
        logger.debug("XML payload validated.")
        Right(ar.toValidatedPayloadRequest(xml))
      }
        .recover {
          case saxe: SAXException =>
            val msg = "Payload did not pass validation against the schema."
            logger.debug(msg, saxe)
            logger.error(msg)
            Left(ErrorResponse
              .errorBadRequest("Payload is not valid according to schema")
              .withErrors(xmlValidationErrors(saxe): _*).XmlResult.withConversationId)
          case NonFatal(e) =>
            val msg = "Error validating payload."
            logger.debug(msg, e)
            logger.error(msg)
            Left(ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId)
        }

    ar.asInstanceOf[AuthorisedRequest[AnyContent]].body.asXml.fold[Future[Either[Result, ValidatedPayloadRequest[A]]]]{
      Future.successful(Left(errorNotWellFormed))
    }{
      xml => validate(xml)
    }
  }

  private def xmlValidationErrors(saxe: SAXException): Seq[ResponseContents] = {
    @annotation.tailrec
    def loop(thr: Exception, acc: List[ResponseContents]): List[ResponseContents] = {
      val newAcc = ResponseContents("xml_validation_error", thr.getMessage) :: acc
      thr match {
        case saxError: SAXException if Option(saxError.getException).isDefined => loop(saxError.getException, newAcc)
        case _ => newAcc
      }
    }

    loop(saxe, Nil)
  }

  override protected def executionContext: ExecutionContext = ec
}

@Singleton
class PayloadContentValidationAction @Inject()(val payloadValidationAction: PayloadValidationAction,
                                               val logger: FileUploadLogger,
                                               val fileUploadConfigService: FileUploadConfigService)
                                              (implicit ec: ExecutionContext)
  extends ActionRefiner[ValidatedPayloadRequest, ValidatedFileUploadPayloadRequest] with HttpStatusCodeShortDescriptions {

  private val declarationIdLabel = "DeclarationID"
  private val documentTypeLabel = "DocumentType"
  private val fileGroupSizeLabel = "FileGroupSize"
  private val fileSequenceNoLabel = "FileSequenceNo"
  private val filesLabel = "Files"
  private val fileLabel = "File"
  private val successRedirectLabel = "SuccessRedirect"
  private val errorRedirectLabel = "ErrorRedirect"

  private val errorMaxFileGroupSizeMsg = s"$fileGroupSizeLabel exceeds ${fileUploadConfigService.fileUploadConfig.fileGroupSizeMaximum} limit"
  private val errorFileGroupSizeMsg = s"$fileGroupSizeLabel does not match number of $fileLabel elements"
  private val errorMaxFileSequenceNoMsg = s"$fileSequenceNoLabel must not be greater than $fileGroupSizeLabel"
  private val errorDuplicateFileSequenceNoMsg = s"$fileSequenceNoLabel contains duplicates"
  private val errorFileSequenceNoLessThanOneMsg = s"$fileSequenceNoLabel must start from 1"
  private val errorErrorRedirectWithoutSuccessRedirectMsg = s"If $errorRedirectLabel is present then $successRedirectLabel must be too"

  private val errorMaxFileGroupSize = ResponseContents(BadRequestCode, errorMaxFileGroupSizeMsg)
  private val errorFileGroupSize = ResponseContents(BadRequestCode, errorFileGroupSizeMsg)
  private val errorMaxFileSequenceNo = ResponseContents(BadRequestCode, errorMaxFileSequenceNoMsg)
  private val errorDuplicateFileSequenceNo = ResponseContents(BadRequestCode, errorDuplicateFileSequenceNoMsg)
  private val errorFileSequenceNoLessThanOne = ResponseContents(BadRequestCode, errorFileSequenceNoLessThanOneMsg)
  private val errorErrorRedirectWithoutSuccessRedirect = ResponseContents(BadRequestCode, errorErrorRedirectWithoutSuccessRedirectMsg)

  override def refine[A](vpr: ValidatedPayloadRequest[A]): Future[Either[Result, ValidatedFileUploadPayloadRequest[A]]] = {
    implicit val validatedFilePayloadRequest: ValidatedPayloadRequest[A] = vpr
    vpr.authorisedAs match {
      case CspWithEori(_, _) | NonCsp(_) =>
        val xml = validatedFilePayloadRequest.xmlBody

        val declarationId = DeclarationId((xml \ declarationIdLabel).text)
        val fileGroupSize = FileGroupSize((xml \ fileGroupSizeLabel).text.trim.toInt)

        val files: Seq[FileUploadFile] = (xml \ filesLabel \ "_").theSeq.collect {
          case file =>
            val fileSequenceNumber = FileSequenceNo((file \ fileSequenceNoLabel).text.trim.toInt)
            val maybeDocumentTypeText = maybeElement(file, documentTypeLabel)
            val documentType = if (maybeElement(file, documentTypeLabel).isEmpty) None else Some(DocumentType(maybeDocumentTypeText.get))
            FileUploadFile(fileSequenceNumber, documentType, maybeElement(file, successRedirectLabel), maybeElement(file, errorRedirectLabel))
          }

        val fileUpload = FileUploadRequest(declarationId, fileGroupSize, files.sortWith(_.fileSequenceNo.value < _.fileSequenceNo.value))

        additionalValidation(fileUpload) match {
          case Right(_) =>
            Future.successful(Right(validatedFilePayloadRequest.toValidatedFileUploadPayloadRequest(fileUpload)))
          case Left(errorResponse) =>
            Future.successful(Left(errorResponse.XmlResult))
        }
      case _ => Future.successful(Left(ErrorResponse(FORBIDDEN, ForbiddenCode, "Not an authorized service").XmlResult.withConversationId))
    }
  }

  private def maybeElement(file: Node, label: String): Option[String] = {
    val elementText = (file \ label).text
    if (elementText.trim.isEmpty) None else Some(elementText)
  }

  private def additionalValidation[A](fileUpload: FileUploadRequest)(implicit vpr: ValidatedPayloadRequest[A]): Either[ErrorResponse, Unit] = {

    def errorRedirectOnly = validate(
      fileUpload,
      { b: FileUploadRequest =>
        !b.files.exists(file => file.errorRedirect.isDefined && file.successRedirect.isEmpty)
      },
      errorErrorRedirectWithoutSuccessRedirect)

    def maxFileGroupSize = validate(
      fileUpload,
      { b: FileUploadRequest =>
        b.fileGroupSize.value <= fileUploadConfigService.fileUploadConfig.fileGroupSizeMaximum},
      errorMaxFileGroupSize)

    def maxFileSequenceNo = validate(
      fileUpload,
      { b: FileUploadRequest =>
        b.fileGroupSize.value >= b.files.last.fileSequenceNo.value },
      errorMaxFileSequenceNo)

    def fileGroupSize = validate(
      fileUpload,
      { b: FileUploadRequest =>
        b.fileGroupSize.value == b.files.length },
      errorFileGroupSize)

    def duplicateFileSequenceNo = validate(
      fileUpload,
      { b: FileUploadRequest =>
        b.files.distinct.length == b.files.length },
      errorDuplicateFileSequenceNo)

    def fileSequenceNoLessThanOne = validate(
      fileUpload,
      { b: FileUploadRequest =>
        b.files.head.fileSequenceNo.value == 1},
      errorFileSequenceNoLessThanOne)

    errorRedirectOnly ++ maxFileGroupSize ++ maxFileSequenceNo ++ fileGroupSize ++ duplicateFileSequenceNo ++ fileSequenceNoLessThanOne match {
      case Seq() => Right(())
      case errors =>
        Left(new ErrorResponse(Status.BAD_REQUEST, BadRequestCode, "Payload did not pass validation", errors: _*))
    }
  }

  private def validate[A](fileUploadRequest: FileUploadRequest,
                          rule: FileUploadRequest => Boolean,
                          responseContents: ResponseContents)(implicit vpr: ValidatedPayloadRequest[A]): Seq[ResponseContents] = {

    def leftWithLogContainingValue(fileUploadRequest: FileUploadRequest, responseContents: ResponseContents) = {
      logger.error(responseContents.message)
      Seq(responseContents)
    }

    if (rule(fileUploadRequest)) Seq() else leftWithLogContainingValue(fileUploadRequest, responseContents)
  }

  override protected def executionContext: ExecutionContext = ec
}
