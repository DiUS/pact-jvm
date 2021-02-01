package specification

import io.pact.core.model.DefaultPactReader
import io.pact.core.support.Json
import io.pact.core.support.json.JsonParser
import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
class BaseResponseSpec extends Specification {

  static List loadTestCases(String testDir) {
    def resources = ResponseSpecificationV1Spec.getResource(testDir)
    def file = new File(resources.toURI())
    def result = []
    file.eachDir { d ->
      d.eachFile { f ->
        def json = f.withReader { JsonParser.INSTANCE.parseReader(it) }
        def jsonMap = Json.INSTANCE.toMap(json)
        def expected = DefaultPactReader.extractResponse(json.asObject().get('expected').asObject())
        def actual = DefaultPactReader.extractResponse(json.asObject().get('actual').asObject())
        if (expected.body.present) {
          expected.setDefaultContentType(expected.body.detectContentType().toString())
        }
        actual.setDefaultContentType(actual.body.present ? actual.body.detectContentType().toString() :
          'application/json')
        result << [d.name, f.name, jsonMap.comment, jsonMap.match, jsonMap.match ? 'should match' : 'should not match',
                   expected, actual]
      }
    }
    result
  }

}
