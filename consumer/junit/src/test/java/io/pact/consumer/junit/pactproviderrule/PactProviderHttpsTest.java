package io.pact.consumer.junit.pactproviderrule;

import io.pact.core.model.annotations.Pact;
import io.pact.consumer.dsl.PactDslWithProvider;
import io.pact.consumer.junit.exampleclients.ConsumerHttpsClient;
import io.pact.consumer.junit.PactHttpsProviderRule;
import io.pact.consumer.junit.PactVerification;
import io.pact.core.model.PactSpecVersion;
import io.pact.core.model.RequestResponsePact;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PactProviderHttpsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PactProviderHttpsTest.class);

    @Rule
    public PactHttpsProviderRule mockTestProvider = new PactHttpsProviderRule("test_provider", "localhost", 10443, true,
      PactSpecVersion.V3, this);

    @Pact(provider="test_provider", consumer="test_consumer")
    public RequestResponsePact createFragment(PactDslWithProvider builder) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("testreqheader", "testreqheadervalue");

        return builder
            .given("good state")
            .uponReceiving("PactProviderTest test interaction")
                .path("/")
                .method("GET")
                .headers(headers)
            .willRespondWith()
                .status(200)
                .headers(headers)
                .body("{\"responsetest\": true, \"name\": \"harry\"}")
            .uponReceiving("PactProviderTest second test interaction")
                .method("OPTIONS")
                .headers(headers)
                .path("/second")
                .body("")
            .willRespondWith()
                .status(200)
                .headers(headers)
                .body("")
            .toPact();
    }

    @Test
    @PactVerification(value = "test_provider")
    public void runTest() throws IOException {
        LOGGER.info("Config: " + mockTestProvider.getConfig());
        Assert.assertEquals(new ConsumerHttpsClient(mockTestProvider.getConfig().url()).options("/second"), 200);
        Map expectedResponse = new HashMap();
        expectedResponse.put("responsetest", true);
        expectedResponse.put("name", "harry");
        assertEquals(new ConsumerHttpsClient(mockTestProvider.getConfig().url()).getAsMap("/", ""), expectedResponse);
    }

    @Test(expected = AssertionError.class)
    @PactVerification("test_provider")
    public void runTestWithUserCodeFailure() throws IOException {
        Assert.assertEquals(new ConsumerHttpsClient(mockTestProvider.getConfig().url()).options("/second"), 200);
        Map expectedResponse = new HashMap();
        expectedResponse.put("responsetest", true);
        expectedResponse.put("name", "fred");
        assertEquals(new ConsumerHttpsClient(mockTestProvider.getConfig().url()).getAsMap("/", ""), expectedResponse);
    }

    @Test
    @Ignore("Can't test this, as the ExpectException statement is applied before the PactHttpsProviderRule rule")
    @PactVerification(value = "test_provider")
    public void runTestWithPactError() throws IOException {
        Assert.assertEquals(new ConsumerHttpsClient(mockTestProvider.getConfig().url()).options("/second"), 200);
    }
}
