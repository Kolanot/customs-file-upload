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

package util

import scala.xml.Elem

object TestXMLData {

  def validFileUploadXml(fileGroupSize: Int = 2, fileSequenceNo1: Int = 1, fileSequenceNo2: Int = 2, includeSuccessRedirect: Boolean = true): Elem =
    <FileUploadRequest
    xmlns="hmrc:fileupload"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <DeclarationID>declarationId</DeclarationID>
      <FileGroupSize>{fileGroupSize}</FileGroupSize>
      <Files>
        <File>
          <FileSequenceNo>{fileSequenceNo1}</FileSequenceNo>
          <DocumentType>document type {fileSequenceNo1}</DocumentType>
          {if (includeSuccessRedirect) <SuccessRedirect>https://success-redirect.com</SuccessRedirect>}
          <ErrorRedirect>https://error-redirect.com</ErrorRedirect>
        </File>
        <File>
          <FileSequenceNo>{fileSequenceNo2}</FileSequenceNo>
          <SuccessRedirect>https://success-redirect.com</SuccessRedirect>
          <ErrorRedirect>https://error-redirect.com</ErrorRedirect>
        </File>
      </Files>
    </FileUploadRequest>

  val InvalidFileUploadXml: Elem =   <FileUploadRequest
  xmlns="hmrc:fileupload"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <DeclarationID foo="bar" >declarationId</DeclarationID>
    <FileGroupSize>1</FileGroupSize>
    <Files>
      <File>
        <FileSequenceNo>1</FileSequenceNo>
        <DocumentType>document type 1</DocumentType>
        <SuccessRedirect>https://success-redirect.com</SuccessRedirect>
        <ErrorRedirect>https://error-redirect.com</ErrorRedirect>
      </File>
    </Files>
  </FileUploadRequest>
  
  val InvalidFileUploadXmlWithTwoErrors: Elem =   <FileUploadRequest
  xmlns="hmrc:fileupload"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <DeclarationID foo="bar" >declarationId</DeclarationID>
    <FileGroupSize>1</FileGroupSize>
    <Files>
      <File>
        <FileSequenceNo>A</FileSequenceNo>
        <DocumentType>document type 1</DocumentType>
        <SuccessRedirect>https://success-redirect.com</SuccessRedirect>
        <ErrorRedirect>https://error-redirect.com</ErrorRedirect>
      </File>
    </Files>
  </FileUploadRequest>

  val InvalidFileUploadXmlWithIntegerError: Elem = <FileUploadRequest
  xmlns="hmrc:fileupload"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <DeclarationID>declarationId</DeclarationID>
    <FileGroupSize>1</FileGroupSize>
    <Files>
      <File>
        <FileSequenceNo>111111111111111111111111111111111111111</FileSequenceNo>
        <DocumentType>document type 1</DocumentType>
        <SuccessRedirect>https://success-redirect.com</SuccessRedirect>
        <ErrorRedirect>https://error-redirect.com</ErrorRedirect>
      </File>
    </Files>
  </FileUploadRequest>

}
