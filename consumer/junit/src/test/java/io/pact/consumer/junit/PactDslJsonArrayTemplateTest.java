package io.pact.consumer.junit;

import io.pact.consumer.MockServer;
import io.pact.consumer.PactTestExecutionContext;
import io.pact.consumer.dsl.DslPart;
import io.pact.consumer.dsl.PactDslJsonArray;
import io.pact.consumer.dsl.PactDslJsonBody;
import io.pact.consumer.dsl.PactDslWithProvider;
import io.pact.consumer.junit.exampleclients.ConsumerClient;
import io.pact.core.model.RequestResponsePact;

public class PactDslJsonArrayTemplateTest extends ConsumerPactTest {
    @Override
    protected RequestResponsePact createPact(PactDslWithProvider builder) {
        DslPart personTemplate = new PactDslJsonBody()
                .id()
                .stringType("name")
                .date("dob");

        DslPart body = new PactDslJsonArray()
                .template(personTemplate, 3);

        RequestResponsePact pact = builder
                .uponReceiving("java test interaction with a DSL array body with templates")
                .path("/")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(body)
                .toPact();

        MatcherTestUtils.assertResponseMatcherKeysEqualTo(pact, "body",
            "$[0].id",
            "$[0].name",
            "$[0].dob",
            "$[1].id",
            "$[1].name",
            "$[1].dob",
            "$[2].id",
            "$[2].name",
            "$[2].dob"
        );

        return pact;
    }

    @Override
    protected String providerName() {
        return "test_provider_array";
    }

    @Override
    protected String consumerName() {
        return "test_consumer_array";
    }

    @Override
    protected void runTest(MockServer mockServer, PactTestExecutionContext context) {
        try {
            new ConsumerClient(mockServer.getUrl()).getAsList("/");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
