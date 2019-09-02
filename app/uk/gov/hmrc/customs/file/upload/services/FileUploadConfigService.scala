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

import cats.implicits._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.customs.api.common.config.{ConfigValidatedNelAdaptor, CustomsValidatedNel}
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.FileUploadConfig

@Singleton
  class FileUploadConfigService @Inject()(configValidatedNel: ConfigValidatedNelAdaptor, logger: FileUploadLogger) {

  private val root = configValidatedNel.root
  private val upscanV1Service = configValidatedNel.service("upscan-initiate-v1")
  private val upscanV2Service = configValidatedNel.service("upscan-initiate-v2")
  private val customsNotificationsService = configValidatedNel.service("customs-notification")
  private val apiSubscriptionFieldsService = configValidatedNel.service("api-subscription-fields")
  private val fileTransmissionService = configValidatedNel.service("file-transmission")

  private val bearerTokenNel = customsNotificationsService.string("bearer-token")
  private val customsNotificationsServiceUrlNel = customsNotificationsService.serviceUrl
  private val apiSubscriptionFieldsServiceUrlNel = apiSubscriptionFieldsService.serviceUrl

  private val upscanV1InitiateUrl = upscanV1Service.serviceUrl
  private val upscanV2InitiateUrl = upscanV2Service.serviceUrl
  private val upscanCallbackUrl = root.string("upscan-callback.url")
  private val fileUploadUpscanCallbackUrl = root.string("file-upload-upscan-callback.url")
  private val fileGroupSizeMaximum = root.int("fileUpload.fileGroupSize.maximum")
  private val fileTransmissionUrl = fileTransmissionService.serviceUrl
  private val fileTransmissionCallbackUrl =  root.string("file-transmission-callback.url")
  private val upscanInitiateMaximumFileSize = root.int("fileUpload.fileSize.maximum")
  private val ttlInSeconds = root.int("ttlInSeconds")

  private val validatedFileUploadConfig: CustomsValidatedNel[FileUploadConfig] = (apiSubscriptionFieldsServiceUrlNel,
    customsNotificationsServiceUrlNel, bearerTokenNel, upscanV1InitiateUrl, upscanV2InitiateUrl, upscanCallbackUrl,
    upscanInitiateMaximumFileSize, fileUploadUpscanCallbackUrl, fileGroupSizeMaximum, fileTransmissionCallbackUrl,
    fileTransmissionUrl, ttlInSeconds
  ) mapN FileUploadConfig

  private val customsConfigHolder =
    validatedFileUploadConfig map CustomsConfigHolder fold(
        fe = { nel =>
          // error case exposes nel (a NotEmptyList)
          val errorMsg = nel.toList.mkString("\n", "\n", "")
          logger.errorWithoutRequestContext(errorMsg)
          throw new IllegalStateException(errorMsg)
        },
        fa = identity
      )

  val fileUploadConfig: FileUploadConfig = customsConfigHolder.validatedFileUploadConfig

  private case class CustomsConfigHolder(validatedFileUploadConfig: FileUploadConfig)
}
