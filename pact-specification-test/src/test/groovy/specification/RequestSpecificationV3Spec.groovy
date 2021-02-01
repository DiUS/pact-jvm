package specification

import io.pact.core.matchers.RequestMatching
import spock.lang.Unroll

class RequestSpecificationV3Spec extends BaseRequestSpec {

  @Unroll
  def '#type/#name - #test #matchDesc'() {
    expect:
    RequestMatching.requestMismatches(expected, actual).matchedOk() == match

    where:
    [type, name, test, match, matchDesc, expected, actual] << loadTestCases('/v3/request/')
  }

}
