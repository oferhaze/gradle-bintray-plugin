package com.jfrog.bintray.gradle

import groovyx.net.http.EncoderRegistry
import groovyx.net.http.HTTPBuilder
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler
import org.apache.http.impl.client.DefaultRedirectStrategy
import org.apache.http.protocol.HttpContext

class BintrayHttpClientFactory {

    static HTTPBuilder create(apiUrl, user, apiKey) {
        def http = new HTTPBuilder(apiUrl)

        // Must use preemptive auth for non-repeatable upload requests
        http.headers.Authorization = "Basic ${"$user:$apiKey".toString().bytes.encodeBase64()}"

        //Set an entity with a length for a stream that has the totalBytes method on it
        def er = new EncoderRegistry() {
            @Override
            InputStreamEntity encodeStream(Object data, Object contentType) throws UnsupportedEncodingException {
                if (data.metaClass.getMetaMethod("totalBytes")) {
                    InputStreamEntity entity = new InputStreamEntity((InputStream) data, data.totalBytes())
                    entity.setContentType(contentType.toString())
                    entity
                } else {
                    super.encodeStream(data, contentType)
                }
            }
        }
        http.encoders = er

        //No point in retrying non-repeatable upload requests
        http.client.httpRequestRetryHandler = new DefaultHttpRequestRetryHandler(0, false)

        //Follow permanent redirects for PUTs
        http.client.setRedirectStrategy(new DefaultRedirectStrategy() {
            @Override
            boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
                def redirected = super.isRedirected(request, response, context)
                return redirected || response.getStatusLine().getStatusCode() == 301
            }

            @Override
            HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context)
                    throws org.apache.http.ProtocolException {
                URI uri = getLocationURI(request, response, context)
                String method = request.requestLine.method
                if (method.equalsIgnoreCase(HttpHead.METHOD_NAME)) {
                    return new HttpHead(uri)
                } else if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) {
                    return new HttpPut(uri)
                } else {
                    return new HttpGet(uri)
                }
            }
        })
        http
    }
}
