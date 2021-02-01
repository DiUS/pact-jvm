package io.pact.consumer.junit;

import io.pact.consumer.dsl.PactDslJsonRootValue;
import io.pact.consumer.dsl.PactDslWithProvider;
import io.pact.core.model.RequestResponsePact;
import io.pact.core.model.annotations.Pact;
import org.apache.http.client.fluent.Request;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class Defect369Test {

    private static final String APPLICATION_JSON = "application/json";
    @Rule
    public PactProviderRule provider = new PactProviderRule("369_provider", this);

    @Pact(provider="369_provider", consumer="test_consumer")
    public RequestResponsePact createFragment(PactDslWithProvider builder) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json");
        return builder
          .uponReceiving("a request for a simple string")
          .path("/provider/uri")
          .method("GET")
          .willRespondWith()
          .status(200)
          .headers(headers)
          .body(PactDslJsonRootValue.stringType("Example"))
          .toPact();
    }

    @Test
    @PactVerification("369_provider")
    public void runTest() throws IOException {
        assertEquals("\"Example\"", Request.Get(provider.getUrl() + "/provider/uri")
            .addHeader("Accept", APPLICATION_JSON)
            .execute().returnContent().asString());
    }
}
