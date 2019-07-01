Once you receive a successful response from this API, use the information in the response to POST your file to the relevant Amazon S3 bucket.

You must use multipart encoding (multipart/form-data) NOT application/x-www-form-urlencoded. If you use application/x-www-form-urlencoded, AWS will return a response where this encoding error is not clear.

The 'file' field must be the last field in the submitted form.
