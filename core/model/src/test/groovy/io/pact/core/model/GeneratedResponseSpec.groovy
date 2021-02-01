package io.pact.core.model

import io.pact.core.model.generators.Category
import io.pact.core.model.generators.GeneratorTestMode
import io.pact.core.model.generators.Generators
import io.pact.core.model.generators.RandomIntGenerator
import io.pact.core.model.generators.RandomStringGenerator
import io.pact.core.model.generators.UuidGenerator
import io.pact.core.support.Json
import io.pact.core.support.json.JsonParser
import spock.lang.Specification

class GeneratedResponseSpec extends Specification {
  private Generators generators
  private Response response

  def setup() {
    generators = new Generators()
    generators.addGenerator(Category.STATUS, new RandomIntGenerator(400, 499))
    generators.addGenerator(Category.HEADER, 'A', UuidGenerator.INSTANCE)
    generators.addGenerator(Category.BODY, '$.a', new RandomStringGenerator())
    response = new Response(generators: generators)
  }

  def 'applies status generator for status to the copy of the response'() {
    given:
    response.status = 200

    when:
    def generated = response.generatedResponse([:], GeneratorTestMode.Provider)

    then:
    generated.status >= 400 && generated.status < 500
  }

  def 'applies header generator for headers to the copy of the response'() {
    given:
    response.headers = [A: 'a', B: 'b']

    when:
    def generated = response.generatedResponse([:], GeneratorTestMode.Provider)

    then:
    generated.headers.A != 'a'
    generated.headers.B == 'b'
  }

  def 'applies body generators for body values to the copy of the response'() {
    given:
    def body = [a: 'A', b: 'B']
    response.body = OptionalBody.body(Json.INSTANCE.prettyPrint(body).bytes)

    when:
    def generated = response.generatedResponse([:], GeneratorTestMode.Provider)
    def generatedBody = Json.INSTANCE.toMap(JsonParser.INSTANCE.parseString(generated.body.valueAsString()))

    then:
    generatedBody.a != 'A'
    generatedBody.b == 'B'
  }

}
