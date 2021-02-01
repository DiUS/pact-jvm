package io.pact.consumer.junit;

import io.pact.consumer.MockServer;
import io.pact.consumer.PactTestExecutionContext;
import io.pact.consumer.dsl.DslPart;
import io.pact.consumer.dsl.PactDslJsonBody;
import io.pact.consumer.dsl.PactDslWithProvider;
import io.pact.core.model.RequestResponsePact;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class Defect320Test extends ConsumerPactTest {

  public RequestResponsePact createPact(PactDslWithProvider builder) {
    DslPart requestDSL = new PactDslJsonBody()
      .stringType("id")
      .stringType("method")
      .stringType("jsonrpc", "2.0")
      .array("params")
        .stringType("QIZ");
    return builder
        .given("test state")
        .uponReceiving("A request for json")
            .path("/json")
            .method("PUT")
            .body(requestDSL)
        .willRespondWith()
            .status(200)
        .toPact();
  }

  @Override
  protected String providerName() {
    return "320_provider";
  }

  @Override
  protected String consumerName() {
    return "test_consumer";
  }

  @Override
  protected void runTest(MockServer mockServer, PactTestExecutionContext context) throws IOException {
    assertEquals(200, Request.Put(mockServer.getUrl() + "/json")
      .addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType())
      .bodyString("{" +
          "\"id\": \"any string\"," +
          "\"method\": \"any string\"," +
          "\"jsonrpc\": \"2.0\"," +
          "\"params\": [\"any string\"]}",
        ContentType.APPLICATION_JSON)
      .execute().returnResponse().getStatusLine().getStatusCode());
  }
}
