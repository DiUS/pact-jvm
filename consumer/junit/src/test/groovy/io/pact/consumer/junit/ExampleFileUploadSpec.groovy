package io.pact.consumer.junit

import io.pact.core.model.annotations.Pact
import io.pact.consumer.dsl.PactDslWithProvider
import io.pact.core.model.RequestResponsePact
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.junit.Rule
import org.junit.Test

class ExampleFileUploadSpec {

  @Rule
  @SuppressWarnings('PublicInstanceField')
  public final PactProviderRule mockProvider = new PactProviderRule('File Service', this)

  @Pact(provider = 'File Service', consumer= 'Junit Consumer')
  RequestResponsePact createPact(PactDslWithProvider builder) {
    builder
      .uponReceiving('a multipart file POST')
      .path('/upload')
      .method('POST')
      .withFileUpload('file', 'data.csv', 'text/csv', '1,2,3,4\n5,6,7,8'.bytes)
      .willRespondWith()
      .status(201)
      .body('file uploaded ok', 'text/plain')
      .toPact()
  }

  @Test
  @PactVerification
  void runTest() {
    CloseableHttpClient httpclient = HttpClients.createDefault()
    httpclient.withCloseable {
      def data = MultipartEntityBuilder.create()
        .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
        .addBinaryBody('file', '1,2,3,4\n5,6,7,8'.bytes, ContentType.create('text/csv'), 'data.csv')
        .build()
      def request = RequestBuilder
        .post(mockProvider.url + '/upload')
        .setEntity(data)
        .build()
      println('Executing request ' + request.requestLine)
      httpclient.execute(request)
    }
  }
}
