# Customs File Upload

The API provides a method of securely uploading additional documentation to support a declaration submission.

This service is used to initiate a file upload as part of the declaration submission process.  An example document you may be requested to upload could be a paper copy of a licence. This endpoint is used to initiate a file upload where a signed URL is returned by the endpoint to be used in the file upload workflow. 

Further documentation of the file upload service is located here:

https://github.com/hmrc/upscan-initiate

## Useful CURL commands for local testing
[link to curl commands](docs/curl-commands.md)

# Lookup of `fieldsId` UUID from `api-subscription-fields` service
The `X-Client-ID` header, together with the application context and version are used
 to call the `api-subscription-fields` service to get the unique `fieldsId` UUID to pass on to the backend request.

There is a direct dependency on the `api-subscription-fields` service. Note the service to get the `fieldsId` is not currently stubbed. 

## Seeding Data in `api-subscription-fields` for local end to end testing

Make sure the [`api-subscription-fields`](https://github.com/hmrc/api-subscription-fields) service is running on port `9650`. Then run the below curl command.

Please note that version `1.0` is used as an example in the commands given and you should insert the customs file upload api version number which you will call subsequently.

Please note that value `d65f2252-9fcf-4f04-9445-5971021226bb` is used as an example in the commands given and you should insert the UUID value which suits your needs.

    curl -v -X PUT "http://localhost:9650/field/application/d65f2252-9fcf-4f04-9445-5971021226bb/context/customs%2Fsupporting-documentation/version/1.0" -H "Cache-Control: no-cache" -H "Content-Type: application/json" -d '{ "fields" : { "callbackUrl" : "https://postman-echo.com/post", "securityToken" : "securityToken" } }'

We then have to manually reset the `fieldId` field to match the id expected by the downstream services. In a mongo command
window paste the following, one after the other.

    use api-subscription-fields

    db.subscriptionFields.update(
        { "clientId" : "d65f2252-9fcf-4f04-9445-5971021226bb", "apiContext" : "customs/supporting-documentation", "apiVersion" : "1.0" },
        { $set:
            {"fieldsId" : "d65f2252-9fcf-4f04-9445-5971021226bb"}
        }
    )
    
When you then send a request to `customs-file-upload` make sure you have the HTTP header `X-Client-ID` with the value `d65f2252-9fcf-4f04-9445-5971021226bb`
    
### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
