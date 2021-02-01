package specification

import io.pact.core.matchers.ResponseMatching
import groovy.util.logging.Slf4j
import spock.lang.Unroll

@Slf4j
class ResponseSpecificationV2Spec extends BaseResponseSpec {

  @Unroll
  def '#type/#name - #test #matchDesc'() {
    expect:
    ResponseMatching.responseMismatches(expected, actual).empty == match

    where:
    [type, name, test, match, matchDesc, expected, actual] << loadTestCases('/v2/response/')
  }

}
