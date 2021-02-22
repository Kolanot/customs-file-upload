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

package component

import java.time.{Instant, ZoneId, ZonedDateTime}

import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.customs.file.upload.services.{DateTimeService, UniqueIdsService}
import util.TestData.stubUniqueIdsService
import util.{CustomsFileUploadExternalServicesConfig, ExternalServicesConfig}

trait ComponentTestSpec extends FeatureSpec
  with GivenWhenThen
  with GuiceOneAppPerSuite
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with Eventually
  with MockitoSugar {

  private val mockDateTimeService =  mock[DateTimeService]

  val dateTime = 1546344000000L // 01/01/2019 12:00:00

  when(mockDateTimeService.nowUtc()).thenReturn(new DateTime(dateTime, DateTimeZone.UTC))
  when(mockDateTimeService.zonedDateTimeUtc).thenReturn(ZonedDateTime.ofInstant(Instant.ofEpochMilli(dateTime), ZoneId.of("UTC")))

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(bind[DateTimeService].toInstance(mockDateTimeService))
    .overrides(bind[UniqueIdsService].toInstance(stubUniqueIdsService))
    .configure(Map(
    "xml.max-errors" -> 2,
    "microservice.services.auth.host" -> ExternalServicesConfig.Host,
    "microservice.services.auth.port" -> ExternalServicesConfig.Port,
    "microservice.services.api-subscription-fields.host" -> ExternalServicesConfig.Host,
    "microservice.services.api-subscription-fields.port" -> ExternalServicesConfig.Port,
    "microservice.services.api-subscription-fields.context" -> CustomsFileUploadExternalServicesConfig.ApiSubscriptionFieldsContext,
    "upscan-initiate.url" -> s"http://${ExternalServicesConfig.Host}:${ExternalServicesConfig.Port}${CustomsFileUploadExternalServicesConfig.UpscanInitiateContextV2}",
    "microservice.services.upscan-initiate-v2.host" -> ExternalServicesConfig.Host,
    "microservice.services.upscan-initiate-v2.port" -> ExternalServicesConfig.Port,
    "microservice.services.upscan-initiate-v2.context" -> CustomsFileUploadExternalServicesConfig.UpscanInitiateContextV2,
    "microservice.services.upscan-initiate-v2.bearer-token" -> ExternalServicesConfig.AuthToken,
    "microservice.services.upscan-initiate-v1.host" -> ExternalServicesConfig.Host,
    "microservice.services.upscan-initiate-v1.port" -> ExternalServicesConfig.Port,
    "microservice.services.upscan-initiate-v1.context" -> CustomsFileUploadExternalServicesConfig.UpscanInitiateContextV1,
    "microservice.services.upscan-initiate-v1.bearer-token" -> ExternalServicesConfig.AuthToken,
    "auditing.enabled" -> false,
    "auditing.consumer.baseUri.host" -> ExternalServicesConfig.Host,
    "auditing.consumer.baseUri.port" -> ExternalServicesConfig.Port,
    "microservice.services.customs-notification.host" -> ExternalServicesConfig.Host,
    "microservice.services.customs-notification.port" -> ExternalServicesConfig.Port,
    "microservice.services.customs-notification.context" -> "/customs-notification/notify",
    "microservice.services.customs-notification.bearer-token" -> CustomsFileUploadExternalServicesConfig.CustomsNotificationAuthHeaderValue,
    "microservice.services.file-transmission.host" -> ExternalServicesConfig.Host,
    "microservice.services.file-transmission.port" -> ExternalServicesConfig.Port,
    "microservice.services.file-transmission.context" -> CustomsFileUploadExternalServicesConfig.FileTransmissionContext
  )).build()

}