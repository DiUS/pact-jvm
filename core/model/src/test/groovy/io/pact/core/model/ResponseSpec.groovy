package io.pact.core.model

import io.pact.core.support.Json
import io.pact.core.support.json.JsonValue
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class ResponseSpec extends Specification {

  def 'delegates to the matching rules to parse matchers'() {
    given:
    def json = [
      matchingRules: [
        'stuff': ['': [matchers: [ [match: 'type'] ] ] ]
      ]
    ]

    when:
    def response = Response.fromJson(Json.INSTANCE.toJson(json).asObject())

    then:
    !response.matchingRules.empty
    response.matchingRules.hasCategory('stuff')
  }

  @SuppressWarnings('UnnecessaryGetter')
  def 'fromMap sets defaults for attributes missing from the map'() {
    expect:
    response.status == 200
    response.headers.isEmpty()
    response.body.missing
    response.matchingRules.empty
    response.generators.empty

    where:
    response = Response.fromJson(new JsonValue.Object())
  }

  @Unroll
  def 'fromMap should handle different number types'() {
    expect:
    Response.fromJson(Json.INSTANCE.toJson([status: statusValue]).asObject()).status == 200

    where:
    statusValue << [200, 200L, 200.0, 200.0G, 200G]
  }

  @Issue('#1288')
  def 'when loading from json, do not split header values'() {
    expect:
    Response.fromJson(Json.INSTANCE.toJson([
      headers: [
        'Expires': 'Sat, 27 Nov 1999 12:00:00 GMT',
        'Content-Type': 'application/json'
      ]
    ]).asObject()).headers == [
      Expires: ['Sat, 27 Nov 1999 12:00:00 GMT'],
      'Content-Type': ['application/json']
    ]
  }
}
