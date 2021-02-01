package io.pact.provider.junit;

import io.pact.provider.junit.target.HttpTarget;
import io.pact.provider.junitsupport.State;
import io.pact.provider.junitsupport.target.Target;
import io.pact.provider.junitsupport.target.TestTarget;
import com.github.restdriver.clientdriver.ClientDriverRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.github.restdriver.clientdriver.RestClientDriver.giveEmptyResponse;
import static com.github.restdriver.clientdriver.RestClientDriver.onRequestTo;

@RunWith(PactRunner.class)
@IsContractTest
public class CustomAnnotationContractTest {
  @ClassRule
  public static final ClientDriverRule embeddedService = new ClientDriverRule(8339);
  private static final Logger LOGGER = LoggerFactory.getLogger(CustomAnnotationContractTest.class);

  @TestTarget
  public final Target target = new HttpTarget(8339);

  @Before
  public void before() {
    embeddedService.addExpectation(
      onRequestTo("/data").withAnyParams(), giveEmptyResponse()
    );
  }

  @State("default")
  public void toDefaultState() {
  }

  @State("state 2")
  public void toSecondState(Map params) {
  }
}
