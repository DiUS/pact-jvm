package io.pact.consumer.junit.examples;

import io.pact.consumer.junit.ConsumerPactTest;
import io.pact.consumer.MockServer;
import io.pact.consumer.PactTestExecutionContext;
import io.pact.consumer.dsl.PactDslWithProvider;
import io.pact.core.model.RequestResponsePact;
import org.apache.http.client.fluent.Request;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class StatusServiceConsumerPactTest extends ConsumerPactTest {

    @Override
    protected RequestResponsePact createPact(PactDslWithProvider builder) {
        Map<String, String> headers = new HashMap();
        headers.put("testreqheader", "testreqheadervalue");

        return builder
            .uponReceiving("status")
            .path("/status")
            .method("GET")
            .headers(headers)
            .willRespondWith()
            .status(200)
            .headers(headers)
            .body("{\"responsetest\":true}").toPact();
    }

    @Override
    protected String providerName() {
        return "provider";
    }

    @Override
    protected String consumerName() {
        return "consumer";
    }

    @Override
    protected void runTest(MockServer mockServer, PactTestExecutionContext context) throws IOException {
        StatusServiceClient statusServiceClient = new StatusServiceClient(mockServer.getUrl());

        String currentQuestionnairePage = statusServiceClient.getCurrentQuestionnairePage(null);

        assertEquals(currentQuestionnairePage, "my_home_1");
    }

    private class StatusServiceClient {
        private String baseUrl;

        public StatusServiceClient(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCurrentQuestionnairePage(Object page) throws IOException {
            Request.Get(baseUrl + "/status")
                .addHeader("testreqheader", "testreqheadervalue")
                .execute();
            return "my_home_1";
        }
    }
}
