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

package util

import org.mockito.ArgumentMatchers._
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.HasConversationId
import util.MockitoPassByNameHelper.PassByNameVerifier

object VerifyLogging {

  def verifyFileUploadLoggerError(message: String)(implicit logger: FileUploadLogger): Unit = {
    verifyFileUploadLogger("error", message)
  }

  def verifyFileUploadLoggerWarn(message: String)(implicit logger: FileUploadLogger): Unit = {
    verifyFileUploadLogger("warn", message)
  }

  private def verifyFileUploadLogger(method: String, message: String)(implicit logger: FileUploadLogger): Unit = {
    PassByNameVerifier(logger, method)
      .withByNameParam(message)
      .withParamMatcher(any[HasConversationId])
      .verify()
  }

}
