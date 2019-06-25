# Customs File Upload Curl Commmand
---
### Endpoint Summary

| Path                                                 |  Method  | Description                                       |
|------------------------------------------------------|----------|---------------------------------------------------|
| [`/upload`](#user-content-post-upload)          |   `POST` | Request permission to upload supporting documents |

--- 
 
### Post Upload 
 #### `POST /upload`

 ##### curl command
```
curl -X POST \
  http://localhost:9000/upload \
  -H 'Accept: application/vnd.hmrc.1.0+xml' \
  -H 'Authorization: Bearer {ADD VALID TOKEN}' \
  -H 'Content-Type: application/xml; charset=utf-8' \
  -H 'X-Badge-Identifier: {Badge Id}' \
  -H 'X-Client-ID: {Valid Client Id}' \
  -H 'X-EORI-Identifier: {Valid EORI}' \
  -H 'cache-control: no-cache' \
  -d '<hmrc:FileUploadRequest xmlns:hmrc="hmrc:fileupload">
  <hmrc:DeclarationID>123</hmrc:DeclarationID>
  <hmrc:FileGroupSize>2</hmrc:FileGroupSize>
  <hmrc:Files>
    <hmrc:File>
      <hmrc:FileSequenceNo>1</hmrc:FileSequenceNo>
      <hmrc:DocumentType>"File2"</hmrc:DocumentType>
      <SuccessRedirect>http://success.com/1</SuccessRedirect>
      <ErrorRedirect>http://error.com/1</ErrorRedirect>
    </hmrc:File>
     <hmrc:File>
      <hmrc:FileSequenceNo>2</hmrc:FileSequenceNo>
      <hmrc:DocumentType>"File3", File4"</hmrc:DocumentType>
      <SuccessRedirect>http://success.com/2</SuccessRedirect>
      <ErrorRedirect>http://error.com/2</ErrorRedirect>
    </hmrc:File>
  </hmrc:Files>
</hmrc:FileUploadRequest>'
 
```
---
