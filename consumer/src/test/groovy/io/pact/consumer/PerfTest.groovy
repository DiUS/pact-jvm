package io.pact.consumer

import io.pact.core.model.PactSpecVersion
import io.pact.core.model.RequestResponsePact
import groovy.util.logging.Slf4j
import io.pact.consumer.model.MockProviderConfig
import io.pact.consumer.model.MockServerImplementation
import org.apache.commons.lang3.time.StopWatch
import org.json.JSONObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

import static io.pact.consumer.ConsumerPactRunnerKt.runConsumerTest

@Slf4j
class PerfTest {

  @ParameterizedTest(name = 'Implementation - {0}')
  @EnumSource(value = MockServerImplementation)
  void test(MockServerImplementation impl) {
    log.info("Starting test for $impl")
    StopWatch stopWatch = new StopWatch()
    stopWatch.start()

    // Define the test data:
    String path = '/mypath/abc/'

    //Header data:
    Map<String, String> headerData = ['Content-Type': 'application/json']

    // Put as JSON object:
    JSONObject bodyExpected = new JSONObject()
    bodyExpected.put('name', 'myName')

    stopWatch.split()
    log.info("Setup: ${stopWatch.splitTime}")

    RequestResponsePact pact = ConsumerPactBuilder
      .consumer('perf_test_consumer')
      .hasPactWith('perf_test_provider')
      .uponReceiving("a request to get values - $impl")
        .path(path)
        .method('GET')
      .willRespondWith()
        .status(200)
        .headers(headerData)
        .body(bodyExpected)
      .toPact()

    stopWatch.split()
    log.info("Setup Fragment: ${stopWatch.splitTime}")

    MockProviderConfig config = new MockProviderConfig('127.0.0.1', 5555, PactSpecVersion.V3, 'http', impl)
    assert runConsumerTest(pact, config) { mockServer, context ->
      stopWatch.split()
      log.info("In Test: ${stopWatch.splitTime}")
      assert new ConsumerClient(mockServer.url).getAsMap(path) == ['name': 'myName']

      stopWatch.split()
      log.info("After Test: ${stopWatch.splitTime}")

      true
    } instanceof PactVerificationResult.Ok

    stopWatch.split()
    log.info("End of Test: ${stopWatch.splitTime}")

    stopWatch.stop()
    log.info(stopWatch.toString())
  }
}
