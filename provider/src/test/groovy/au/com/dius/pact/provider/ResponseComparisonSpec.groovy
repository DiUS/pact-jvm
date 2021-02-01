package au.com.dius.pact.provider

import io.pact.core.matchers.MatchingContext
import io.pact.core.model.ContentType
import io.pact.core.model.OptionalBody
import io.pact.core.model.Response
import io.pact.core.model.generators.Generators
import io.pact.core.model.matchingrules.MatchingRuleCategory
import io.pact.core.model.matchingrules.MatchingRulesImpl
import io.pact.core.model.messaging.Message
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import spock.lang.Specification
import spock.lang.Unroll

@SuppressWarnings(['UnnecessaryGetter', 'LineLength'])
class ResponseComparisonSpec extends Specification {

  private Closure<Map> subject
  private Response response
  private int actualStatus
  private Map actualHeaders = ['A': ['B'], 'C': ['D'], 'Content-Type': ['application/json']]
  private actualBody

  void setup() {
    response = new Response(200, ['A': ['mismatch'], 'Content-Type': ['application/json']],
      OptionalBody.body('{"stuff": "is good"}'.bytes))
    actualStatus = 200
    actualBody = '{"stuff": "is good"}'
    subject = { opts = [:] ->
      def status = opts.actualStatus ?: actualStatus
      def response = opts.response ?: response
      def actualHeaders = opts.actualHeaders ?: actualHeaders
      ResponseComparison.compareResponse(response,
        new ProviderResponse(status, actualHeaders, ContentType.JSON, actualBody))
    }
  }

  def 'compare the status should, well, compare the status'() {
    expect:
    subject().statusMismatch == null
    subject(actualStatus: 400).statusMismatch.description() == 'expected status of 200 but was 400'
  }

  def 'should not compare headers if there are no expected headers'() {
    given:
    response = new Response(200, [:], OptionalBody.body(''.bytes))

    expect:
    subject().headerMismatches.isEmpty()
  }

  def 'should only compare the expected headers'() {
    given:
    actualHeaders = ['A': ['B'], 'C': ['D']]
    def response = new Response(200, ['A': ['B']], OptionalBody.body(''.bytes))
    def response2 = new Response(200, ['A': ['D']], OptionalBody.body(''.bytes))

    expect:
    subject(actualHeaders: actualHeaders, response: response).headerMismatches.isEmpty()
    subject(actualHeaders: actualHeaders, response: response2).headerMismatches.A*.description() ==
      ['Expected header \'A\' to have value \'D\' but was \'B\'']
  }

  def 'ignores case in header comparisons'() {
    given:
    actualHeaders = ['A': ['B'], 'C': ['D']]
    response = new Response(200, ['a': ['B']], OptionalBody.body(''.bytes))

    expect:
    subject().headerMismatches.isEmpty()
  }

  def 'comparing bodies should fail with different content types'() {
    given:
    actualHeaders['Content-Type'] = ['text/plain']

    when:
    def result = subject().bodyMismatches

    then:
    result instanceof Err
    result.error.description() ==
      'Expected a response type of \'application/json\' but the actual type was \'text/plain\''
  }

  def 'comparing bodies should pass with the same content types and body contents'() {
    given:
    def result = subject().bodyMismatches

    expect:
    result instanceof Ok
    result.value.mismatches.isEmpty()
  }

  def 'comparing bodies should pass when the order of elements in the actual response is different'() {
    given:
    response = new Response(200, ['Content-Type': ['application/json']], OptionalBody.body(
            '{"moar_stuff": {"a": "is also good", "b": "is even better"}, "stuff": "is good"}'.bytes))
    actualBody = '{"stuff": "is good", "moar_stuff": {"b": "is even better", "a": "is also good"}}'
    def result = subject().bodyMismatches

    expect:
    result instanceof Ok
    result.value.mismatches.isEmpty()
  }

  def 'comparing bodies should show all the differences'() {
    given:
    actualBody = '{"stuff": "should make the test fail"}'
    def result = subject().bodyMismatches

    expect:
    result instanceof Ok
    result.value.mismatches.collectEntries { [ it.key, it.value*.description() ] } == [
      '$.stuff': ["Expected 'is good' (String) but received 'should make the test fail' (String)"]
    ]
    result.value.diff[1] == '-  "stuff": "is good"'
    result.value.diff[2] == '+  "stuff": "should make the test fail"'
  }

  @Unroll
  def 'when comparing message bodies, handles content type #contentType'() {
    given:
    Message expectedMessage = new Message('test', [], OptionalBody.body(expected.bytes),
      new MatchingRulesImpl(), new Generators(), [contentType: contentType])
    OptionalBody actualMessage = OptionalBody.body(actual.bytes)
    def bodyContext = new MatchingContext(new MatchingRuleCategory('body'), true)

    expect:
    ResponseComparison.compareMessageBody(expectedMessage, actualMessage, bodyContext).empty

    where:

    contentType                                | expected                    | actual
    'application/json'                         | '{"a": 100.0, "b": "test"}' | '{"a":100.0,"b":"test"}'
    'application/json;charset=UTF-8'           | '{"a": 100.0, "b": "test"}' | '{"a":100.0,"b":"test"}'
    'application/json; charset\u003dUTF-8'     | '{"a": 100.0, "b": "test"}' | '{"a":100.0,"b":"test"}'
    'application/hal+json; charset\u003dUTF-8' | '{"a": 100.0, "b": "test"}' | '{"a":100.0,"b":"test"}'
    'text/plain'                               | '{"a": 100.0, "b": "test"}' | '{"a": 100.0, "b": "test"}'
    'application/octet-stream;charset=UTF-8'   | '{"a": 100.0, "b": "test"}' | '{"a": 100.0, "b": "test"}'
    'application/octet-stream'                 | '{"a": 100.0, "b": "test"}' | '{"a": 100.0, "b": "test"}'
    ''                                         | '{"a": 100.0, "b": "test"}' | '{"a": 100.0, "b": "test"}'
    null                                       | '{"a": 100.0, "b": "test"}' | '{"a": 100.0, "b": "test"}'

  }
}
