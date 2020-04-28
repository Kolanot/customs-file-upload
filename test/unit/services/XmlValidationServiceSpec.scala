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

package unit.services

import java.io.FileNotFoundException

import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.test.Helpers
import uk.gov.hmrc.customs.file.upload.services.XmlValidationService
import util.TestXMLData.{InvalidFileUploadXml, InvalidFileUploadXmlWithIntegerError, InvalidFileUploadXmlWithTwoErrors, validFileUploadXml}
import util.UnitSpec

import scala.xml.{Node, SAXException}


class XmlValidationServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private implicit val ec = Helpers.stubControllerComponents().executionContext
  protected val mockConfiguration = mock[Configuration]
  protected val mockXml = mock[Node]

  protected val propertyName: String = "xsd.locations.fileupload"

  protected val xsdLocations: Seq[String] = Seq("/api/conf/1.0/schemas/fileupload/FileUploadRequest.xsd")

  def xmlValidationService: XmlValidationService = new XmlValidationService(mockConfiguration){}

  override protected def beforeEach() {
    reset(mockConfiguration)
    when(mockConfiguration.getOptional[Seq[String]](propertyName)).thenReturn(Some(xsdLocations))
    when(mockConfiguration.getOptional[Int]("xml.max-errors")).thenReturn(None)
  }

  "XmlValidationService" should {
    "get location of xsd resource files from configuration" in  {
      await(xmlValidationService.validate(validFileUploadXml()))
      verify(mockConfiguration).getOptional[Seq[String]](propertyName)
    }

    "fail the future when in configuration there are no locations of xsd resource files" in {
      when(mockConfiguration.getOptional[Seq[String]](propertyName)).thenReturn(None)
      
      val caught = intercept[IllegalStateException]{
        await(xmlValidationService.validate(mockXml))
      }

      caught.getMessage shouldBe s"application.conf is missing mandatory property '$propertyName'"
    }

    "fail the future when in configuration there is an empty list for locations of xsd resource files" in {
      when(mockConfiguration.getOptional[Seq[String]](propertyName)).thenReturn(Some(Nil))

      val caught = intercept[IllegalStateException] {
        await(xmlValidationService.validate(mockXml))
      }

      caught.getMessage shouldBe s"application.conf is missing mandatory property '$propertyName'"
    }

    "fail the future when a configured xsd resource file cannot be found" in {
      when(mockConfiguration.getOptional[Seq[String]](propertyName)).thenReturn(Some(List("there/is/no/such/file")))

      val caught = intercept[FileNotFoundException] {
        await(xmlValidationService.validate(mockXml))
      }

      caught.getMessage shouldBe "XML Schema resource file: there/is/no/such/file"
    }

    "successfully validate a correct xml" in {
      val result = await(xmlValidationService.validate(validFileUploadXml()))

      result should be(())
    }

    "fail the future with SAXException when there is an error in XML" in {
      val caught = intercept[SAXException] {
        await(xmlValidationService.validate(InvalidFileUploadXml))
      }

      caught.getMessage shouldBe "cvc-type.3.1.1: Element 'DeclarationID' is a simple type, so it cannot have attributes, excepting those whose namespace name is identical to 'http://www.w3.org/2001/XMLSchema-instance' and whose [local name] is one of 'type', 'nil', 'schemaLocation' or 'noNamespaceSchemaLocation'. However, the attribute, 'foo' was found."

      Option(caught.getException) shouldBe None
    }

    "fail the future with wrapped SAXExceptions when there are multiple errors in XML" in {
      val caught = intercept[SAXException] {
        await(xmlValidationService.validate(InvalidFileUploadXmlWithTwoErrors))
      }
      caught.getMessage shouldBe "cvc-type.3.1.3: The value 'A' of element 'FileSequenceNo' is not valid."

      Option(caught.getException) shouldBe 'nonEmpty
      val wrapped1 = caught.getException
      wrapped1.getMessage shouldBe "cvc-datatype-valid.1.2.1: 'A' is not a valid value for 'integer'."
      wrapped1.isInstanceOf[SAXException] shouldBe true

      Option(wrapped1.asInstanceOf[SAXException].getException) shouldBe 'nonEmpty
      val wrapped2 = wrapped1.asInstanceOf[SAXException].getException
      wrapped2.getMessage shouldBe "cvc-type.3.1.1: Element 'DeclarationID' is a simple type, so it cannot have attributes, excepting those whose namespace name is identical to 'http://www.w3.org/2001/XMLSchema-instance' and whose [local name] is one of 'type', 'nil', 'schemaLocation' or 'noNamespaceSchemaLocation'. However, the attribute, 'foo' was found."
      wrapped2.isInstanceOf[SAXException] shouldBe true

      Option(wrapped2.asInstanceOf[SAXException].getException) shouldBe None
    }

    "fail the future with wrapped SAXExceptions when XML has an integer error" in {
      val caught = intercept[SAXException] {
        await(xmlValidationService.validate(InvalidFileUploadXmlWithIntegerError))
      }
      caught.getMessage shouldBe "cvc-type.3.1.3: The value '111111111111111111111111111111111111111' of element 'FileSequenceNo' is not valid."

      Option(caught.getException) shouldBe 'nonEmpty
      val wrapped1 = caught.getException
      wrapped1.getMessage shouldBe "cvc-maxInclusive-valid: Value '111111111111111111111111111111111111111' is not facet-valid with respect to maxInclusive '2147483647' for type 'MinOneInt'."
      wrapped1.isInstanceOf[SAXException] shouldBe true
    }

    "fail the future with configured number of wrapped SAXExceptions when there are multiple errors in XML" in {
      when(mockConfiguration.getOptional[Int]("xml.max-errors")).thenReturn(Some(2))

      val caught = intercept[SAXException] {
        await(xmlValidationService.validate(InvalidFileUploadXmlWithTwoErrors))
      }
      verify(mockConfiguration).getOptional[Int]("xml.max-errors")

      caught.getMessage shouldBe "cvc-datatype-valid.1.2.1: 'A' is not a valid value for 'integer'."

      Option(caught.getException) shouldBe 'nonEmpty
      val wrapped1 = caught.getException
      wrapped1.getMessage shouldBe "cvc-type.3.1.1: Element 'DeclarationID' is a simple type, so it cannot have attributes, excepting those whose namespace name is identical to 'http://www.w3.org/2001/XMLSchema-instance' and whose [local name] is one of 'type', 'nil', 'schemaLocation' or 'noNamespaceSchemaLocation'. However, the attribute, 'foo' was found."
      wrapped1.isInstanceOf[SAXException] shouldBe true

      Option(wrapped1.asInstanceOf[SAXException].getException) shouldBe None
    }

    "fail the future with system error when a configured maximum of xml errors is not a positive number" in {
      when(mockConfiguration.getOptional[Int]("xml.max-errors")).thenReturn(Some(0))

      val caught = intercept[IllegalArgumentException] {
        await(xmlValidationService.validate(mockXml))
      }

      caught.getMessage shouldBe "requirement failed: maxErrors should be a positive number but 0 was provided instead."
    }
  }
}
