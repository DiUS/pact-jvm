package io.pact.consumer.specs2

import io.pact.consumer.PactVerificationResult
import io.pact.core.matchers.BodyMismatch
import io.pact.core.matchers.HeaderMismatch
import io.pact.core.matchers.Mismatch
import io.pact.core.matchers.PathMismatch
import io.pact.core.model.OptionalBody
import io.pact.core.model.Request
import io.pact.core.model.Response
import scala.collection.JavaConverters
import spock.lang.Specification

class PrettyPrinterSpec extends Specification {

    def headers = [testreqheader: 'testreqheadervalue', 'Content-Type': 'application/json']
    def request = new Request('POST', '/', [:], headers, OptionalBody.body('{"test": true}'.bytes))
    def response = new Response(200, [testreqheader: 'testreqheaderval', 'Access-Control-Allow-Origin': '*'],
      OptionalBody.body('{"responsetest": true}'.bytes))

    def printMismatch(Mismatch mismatch) {
      PrettyPrinter.print(JavaConverters.asScalaBuffer(
        [new PactVerificationResult.PartialMismatch([mismatch])]).toSeq())
    }

    def plus = '+++ '

    def 'header mismatch'() {
        expect:
        printMismatch(new HeaderMismatch('foo', 'bar', '', '')) ==
                """--- Header foo
                |$plus
                |@@ -1,1 +1,1 @@
                |-bar
                |+""".stripMargin()
    }

    def 'path mismatch'() {
        expect:
        printMismatch(new PathMismatch('/foo/bar', '/foo/baz')) ==
              """--- Path
              |$plus
              |@@ -1,1 +1,1 @@
              |-/foo/bar
              |+/foo/baz""".stripMargin()
    }

    def 'body mismatch'() {
        expect:
        printMismatch(new BodyMismatch('{"foo": "bar"}', '{"ork": "Bif"}', '')) ==
              """--- Body
              |$plus
              |@@ -1,3 +1,3 @@
              | {
              |-  "foo": "bar"
              |+  "ork": "Bif"
              | }""".stripMargin()
    }
}
