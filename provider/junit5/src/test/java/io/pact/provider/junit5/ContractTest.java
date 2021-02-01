package io.pact.provider.junit5;

import io.pact.core.model.Interaction;
import io.pact.core.model.Pact;
import io.pact.provider.junitsupport.Provider;
import io.pact.provider.junitsupport.State;
import io.pact.provider.junitsupport.StateChangeAction;
import io.pact.provider.junitsupport.loader.PactFolder;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.http.HttpRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.lang.String.format;

@Provider("myAwesomeService")
@PactFolder("pacts")
@ExtendWith({
  WiremockResolver.class,
  WiremockUriResolver.class
})
public class ContractTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContractTest.class);

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void testTemplate(Pact pact, Interaction interaction, HttpRequest request, PactVerificationContext context) {
      LOGGER.info("testTemplate called: " + pact.getProvider().getName() + ", " + interaction.getDescription());
      request.addHeader("X-ContractTest", "true");

      context.verifyInteraction();
    }

    @BeforeAll
    static void setUpService() {
        //Run DB, create schema
        //Run service
        //...
      LOGGER.info("BeforeAll - setUpService ");
    }

    @BeforeEach
    void before(PactVerificationContext context, @WiremockResolver.Wiremock WireMockServer server,
                @WiremockUriResolver.WiremockUri String uri) throws MalformedURLException {
        // Rest data
        // Mock dependent service responses
        // ...
      LOGGER.info("BeforeEach - " + uri);

      context.setTarget(HttpTestTarget.fromUrl(new URL(uri)));

      server.stubFor(
        get(urlPathEqualTo("/data"))
          .withHeader("X-ContractTest", equalTo("true"))
          .withQueryParam("ticketId", matching("0000|1234|99987"))
          .willReturn(aResponse()
            .withStatus(204)
            .withHeader("Location", format("http://localhost:%s/ticket/%s", server.port(), "1234")
            )
            .withHeader("X-Ticket-ID", "1234"))
      );
    }

    @State("default")
    public Map<String, Object> toDefaultState() {
      // Prepare service before interaction that require "default" state
      // ...
      LOGGER.info("Now service in default state");

      HashMap<String, Object> map = new HashMap<>();
      map.put("ticketId", "1234");
      return map;
    }

    @State("state 2")
    public Map<String, Object> toSecondState(Map params) {
      // Prepare service before interaction that require "state 2" state
      // ...
      LOGGER.info("Now service in 'state 2' state: " + params);
      HashMap<String, Object> map = new HashMap<>();
      map.put("ticketId", "99987");
      return map;
    }

    @State(value = "default", action = StateChangeAction.TEARDOWN)
    public void toDefaultStateAfter() {
      // Cleanup service after interaction that require "default" state
      // ...
      LOGGER.info("Default state teardown");
    }

    @State(value = "state 2", action = StateChangeAction.TEARDOWN)
    public void toSecondStateAfter(Map params) {
      // Cleanup service after interaction that require "state 2" state
      // ...
      LOGGER.info("'state 2' state teardown: " + params);
    }
}
