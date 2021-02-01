package io.pact.provider.junit;

import io.pact.provider.junit.target.HttpTarget;
import io.pact.provider.junitsupport.Provider;
import io.pact.provider.junitsupport.loader.PactFolder;
import io.pact.provider.junitsupport.target.Target;
import io.pact.provider.junitsupport.target.TestTarget;
import com.github.restdriver.clientdriver.ClientDriverRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.restdriver.clientdriver.RestClientDriver.giveEmptyResponse;
import static com.github.restdriver.clientdriver.RestClientDriver.onRequestTo;

@RunWith(ExpectedToFailPactRunner.class)
@Provider("myAwesomeService")
@PactFolder("pacts")
public class ProviderStateTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderStateTest.class);

    @ClassRule
    public static final ClientDriverRule embeddedService = new ClientDriverRule(8333);

    @TestTarget
    public final Target target = new HttpTarget(8333);

    @Before
    public void before() {
      embeddedService.noFailFastOnUnexpectedRequest();
      embeddedService.addExpectation(
        onRequestTo("/data").withAnyParams(), giveEmptyResponse()
      );
    }

    @After
    public void after() {
      embeddedService.reset();
    }
}
