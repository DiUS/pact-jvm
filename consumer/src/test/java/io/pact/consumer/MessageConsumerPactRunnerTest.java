package io.pact.consumer;

import io.pact.consumer.dsl.PactDslJsonBody;
import io.pact.core.model.Pact;
import io.pact.core.model.PactSpecVersion;
import org.junit.Test;

import static io.pact.consumer.ConsumerPactRunnerKt.runMessageConsumerTest;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class MessageConsumerPactRunnerTest {

    @Test
    public void testRunMessageConsumerTestWithPassingTest() {
        PactDslJsonBody content = new PactDslJsonBody();
        content.stringType("sampleContentFieldName", "exampleValue");

        Pact pact = MessagePactBuilder
                .consumer("async_ping_consumer")
                .hasPactWith("async_ping_provider")
                .expectsToReceive("a message")
                .withContent(content)
                .toPact();

        PactVerificationResult result = runMessageConsumerTest(pact, PactSpecVersion.V3, (messages, context) -> {
            assertEquals(messages.size(), 1);
            assertEquals(messages.get(0).contentsAsString(), "{\"sampleContentFieldName\":\"exampleValue\"}");
            return true;
        });

        if (result instanceof PactVerificationResult.Error) {
            throw new RuntimeException(((PactVerificationResult.Error) result).getError());
        }

        assertThat(result, is(instanceOf(PactVerificationResult.Ok.class)));
    }

    @Test
    public void testRunMessageConsumerTestWithFailingTest() {
        PactDslJsonBody content = new PactDslJsonBody();
        content.stringType("sampleContentFieldName", "exampleValue");

        Pact pact = MessagePactBuilder
                .consumer("async_ping_consumer")
                .hasPactWith("async_ping_provider")
                .expectsToReceive("another message")
                .withContent(content)
                .toPact();

        PactVerificationResult result = runMessageConsumerTest(pact, PactSpecVersion.V3, (messages, context) -> {
            assertEquals(messages.size(), 1);
            assertEquals(messages.get(0).contentsAsString(), "{\"sampleContentFieldName\":\"not the correct value\"}");
            return false;
        });

        assertThat(result, is(instanceOf(PactVerificationResult.Error.class)));
    }

}
