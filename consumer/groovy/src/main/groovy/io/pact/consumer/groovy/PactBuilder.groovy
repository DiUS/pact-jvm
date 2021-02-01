package io.pact.consumer.groovy

import io.pact.consumer.Headers
import io.pact.consumer.MockServer
import io.pact.consumer.PactTestExecutionContext
import io.pact.consumer.PactVerificationResult
import io.pact.consumer.model.MockProviderConfig
import io.pact.consumer.model.MockServerImplementation
import io.pact.core.model.Consumer
import io.pact.core.model.OptionalBody
import io.pact.core.model.PactSpecVersion
import io.pact.core.model.Provider
import io.pact.core.model.ProviderState
import io.pact.core.model.RequestResponseInteraction
import io.pact.core.model.RequestResponsePact
import io.pact.core.model.generators.Generators
import io.pact.core.model.matchingrules.MatchingRuleCategory
import io.pact.core.model.matchingrules.MatchingRules
import io.pact.core.model.matchingrules.RegexMatcher
import groovy.transform.CompileStatic
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder

import static io.pact.consumer.ConsumerPactRunnerKt.runConsumerTest

/**
 * Builder DSL for Pact tests
 */
@SuppressWarnings('PropertyName')
class PactBuilder extends GroovyBuilder {

  RequestResponsePact pact = new RequestResponsePact(new Provider(), new Consumer())
  Integer port = 0
  RequestResponseInteraction currentInteraction
  List interactions = []
  List<ProviderState> providerStates = []
  boolean requestState

  /**
   * Defines the service consumer
   * @param consumer consumer name
   */
  PactBuilder serviceConsumer(String consumer) {
    this.pact.consumer = new Consumer(consumer)
    this
  }

  /**
   * Defines the provider the consumer has a pact with
   * @param provider provider name
   */
  PactBuilder hasPactWith(String provider) {
    this.pact.provider = new Provider(provider)
    this
  }

  /**
   * Defines the port the provider will listen on
   * @param port port number
   */
  @SuppressWarnings('ConfusingMethodName')
  PactBuilder port(int port) {
    this.port = port
    this
  }

  /**
   * Defines the provider state the provider needs to be in for the interaction
   * @param providerState provider state description
   */
  @CompileStatic
  PactBuilder given(String providerState) {
    if (requestState && currentInteraction != null) {
      currentInteraction.providerStates << new ProviderState(providerState)
    } else {
      this.providerStates << new ProviderState(providerState)
    }
    this
  }

  /**
   * Defines the provider state the provider needs to be in for the interaction
   * @param providerState provider state description
   * @param params Data parameters for the provider state
   */
  @CompileStatic
  PactBuilder given(String providerState, Map params) {
    if (requestState && currentInteraction != null) {
      currentInteraction.providerStates << new ProviderState(providerState, params)
    } else {
      this.providerStates << new ProviderState(providerState, params)
    }
    this
  }

  /**
   * Defines the start of an interaction
   * @param requestDescription Description of the interaction. Must be unique.
   */
  PactBuilder uponReceiving(String requestDescription) {
    updateInteractions()
    this.currentInteraction = new RequestResponseInteraction(requestDescription, providerStates)
    requestState = true
    providerStates = []
    this
  }

  def updateInteractions() {
    if (currentInteraction) {
      interactions << currentInteraction
    }
  }

  /**
   * Defines the request attributes (body, headers, etc.)
   * @param requestData Map of attributes
   */
  PactBuilder withAttributes(Map requestData) {
    MatchingRules requestMatchers = currentInteraction.request.matchingRules
    Generators requestGenerators = currentInteraction.request.generators
    Map headers = setupHeaders(requestData.headers ?: [:], requestMatchers, requestGenerators)
    Map query = setupQueryParameters(requestData.query ?: [:], requestMatchers, requestGenerators)
    String path = setupPath(requestData.path ?: '/', requestMatchers, requestGenerators)
    this.currentInteraction.request.method = requestData.method ?: 'GET'
    this.currentInteraction.request.headers = headers
    this.currentInteraction.request.query = query
    this.currentInteraction.request.path = path
    def requestBody = setupBody(requestData, currentInteraction.request)
    this.currentInteraction.request.body = requestBody
    this
  }

  /**
   * Defines the response attributes (body, headers, etc.) that are returned for the request
   * @param responseData Map of attributes
   * @return
   */
  @SuppressWarnings('DuplicateMapLiteral')
  PactBuilder willRespondWith(Map responseData) {
    MatchingRules responseMatchers = currentInteraction.response.matchingRules
    Generators responseGenerators = currentInteraction.response.generators
    Map responseHeaders = setupHeaders(responseData.headers ?: [:], responseMatchers, responseGenerators)
    this.currentInteraction.response.status = responseData.status ?: 200
    this.currentInteraction.response.headers = responseHeaders
    def responseBody = setupBody(responseData, currentInteraction.response)
    this.currentInteraction.response.body = responseBody
    requestState = false
    this
  }

  /**
   * Allows the body to be defined using a Groovy builder pattern
   * @param options The following options are available:
   *   - mimeType Optional mimetype for the body
   *   - prettyPrint If the body should be pretty printed
   * @param closure Body closure
   */
  PactBuilder withBody(Map options = [:], Closure closure) {
    def body = new PactBodyBuilder(mimetype: options.mimeType, prettyPrintBody: options.prettyPrint)
    closure.delegate = body
    def result = closure.call()
    if (result instanceof Matcher) {
      throw new InvalidMatcherException('Detected an invalid use of the matchers. ' +
        'If you are using matchers like "eachLike" they need to be assigned to something. For instance:\n' +
        '  `fruits eachLike(1)` or `id = integer()`'
      )
    }
    setupRequestOrResponse(body, options)
    this
  }

  /**
   * Allows the body to be defined using a Groovy builder pattern with an array as the root
   * @param options The following options are available:
   *   - mimeType Optional mimetype for the body
   *   - prettyPrint If the body should be pretty printed
   * @param array body
   */
  PactBuilder withBody(Map options = [:], List array) {
    def body = new PactBodyBuilder(mimetype: options.mimeType, prettyPrintBody: options.prettyPrint)
    body.bodyRepresentation = body.build(array)
    setupRequestOrResponse(body, options)
    this
  }

  /**
   * Allows the body to be defined using a Groovy builder pattern with an array as the root
   * @param options The following options are available:
   *   - mimeType Optional mimetype for the body
   *   - prettyPrint If the body should be pretty printed
   * @param matcher body
   */
  PactBuilder withBody(Map options = [:], LikeMatcher matcher) {
    def body = new PactBodyBuilder(mimetype: options.mimetype, prettyPrintBody: options.prettyPrint)
    body.bodyRepresentation = body.build(matcher)
    setupRequestOrResponse(body, options)
    this
  }

  private setupRequestOrResponse(PactBodyBuilder body, Map options) {
    if (requestState) {
      if (!currentInteraction.request.contentTypeHeader()) {
        if (options.mimeType) {
          currentInteraction.request.headers[CONTENT_TYPE] = [ options.mimeType ]
        } else {
          currentInteraction.request.headers[CONTENT_TYPE] = [ JSON ]
        }
      }
      currentInteraction.request.body = body.body instanceof OptionalBody ? body.body :
        OptionalBody.body(body.body.bytes)
      currentInteraction.request.matchingRules.addCategory(body.matchers)
      currentInteraction.request.generators.addGenerators(body.generators)
    } else {
      if (!currentInteraction.response.contentTypeHeader()) {
        if (options.mimeType) {
          currentInteraction.response.headers[CONTENT_TYPE] = [ options.mimeType ]
        } else {
          currentInteraction.response.headers[CONTENT_TYPE] = [ JSON ]
        }
      }
      currentInteraction.response.body = body.body instanceof OptionalBody ? body.body :
        OptionalBody.body(body.body.bytes)
      currentInteraction.response.matchingRules.addCategory(body.matchers)
      currentInteraction.response.generators.addGenerators(body.generators)
    }
  }

  /**
   * Executes the providers closure in the context of the interactions defined on this builder.
   * @param options Optional map of options for the run
   * @param closure Test to execute
   * @return The result of the test run
   */
  @CompileStatic
  PactVerificationResult runTest(Map options = [:], Closure closure) {
    updateInteractions()
    this.pact.interactions = interactions

    def pactVersion = options.specificationVersion ?: PactSpecVersion.V3
    MockProviderConfig config = MockProviderConfig.httpConfig(LOCALHOST, port ?: 0, pactVersion as PactSpecVersion,
      MockServerImplementation.Default)

    def runTest = closure
    if (closure.maximumNumberOfParameters < 2) {
      if (closure.maximumNumberOfParameters == 1) {
        runTest =  { MockServer server, PactTestExecutionContext context -> closure.call(server) }
      } else {
        runTest =  { MockServer server, PactTestExecutionContext context -> closure.call() }
      }
    }

    runConsumerTest(pact, config, runTest)
  }

  /**
   * Runs the test (via the runTest method), and throws an exception if it was not successful.
   * @param options Optional map of options for the run
   * @param closure
   */
  @SuppressWarnings('InvertedIfElse')
  void runTestAndVerify(Map options = [:], Closure closure) {
    PactVerificationResult result = runTest(options, closure)
    if (!(result instanceof PactVerificationResult.Ok)) {
      if (result instanceof PactVerificationResult.Error) {
        if (!(result.mockServerState instanceof PactVerificationResult.Ok)) {
          throw new AssertionError('Pact Test function failed with an exception, possibly due to ' +
            result.mockServerState, result.error)
        } else {
          throw new AssertionError('Pact Test function failed with an exception: ' + result.error.message, result.error)
        }
      }
      throw new PactFailedException(result)
    }
  }

  /**
   * Sets up a file upload request using a multipart FORM POST. This will add the correct content type header to
   * the request
   * @param partName This is the name of the part in the multipart body.
   * @param fileName This is the name of the file that was uploaded
   * @param fileContentType This is the content type of the uploaded file
   * @param data This is the actual file contents
   */
  void withFileUpload(String partName, String fileName, String fileContentType, byte[] data) {
    def multipart = MultipartEntityBuilder.create()
      .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
      .addBinaryBody(partName, data, ContentType.create(fileContentType), fileName)
      .build()
    ByteArrayOutputStream os = new ByteArrayOutputStream()
    multipart.writeTo(os)
    if (requestState) {
      currentInteraction.request.body = OptionalBody.body(os.toByteArray())
      currentInteraction.request.headers[CONTENT_TYPE] = [ multipart.contentType.value ]
      MatchingRuleCategory category  = currentInteraction.request.matchingRules.addCategory(HEADER)
      category.addRule(CONTENT_TYPE, new RegexMatcher(Headers.MULTIPART_HEADER_REGEX, multipart.contentType.value))
    } else {
      currentInteraction.response.body = OptionalBody.body(os.toByteArray())
      currentInteraction.response.headers[CONTENT_TYPE] = [ multipart.contentType.value ]
      MatchingRuleCategory category  = currentInteraction.response.matchingRules.addCategory(HEADER)
      category.addRule(CONTENT_TYPE, new RegexMatcher(Headers.MULTIPART_HEADER_REGEX, multipart.contentType.value))
    }
  }

  /**
   * Marks a item as to be injected from the provider state
   * @param expression Expression to lookup in the provider state context
   * @param exampleValue Example value to use in the consumer test
   * @return example value
   */
  def fromProviderState(String expression, def exampleValue) {
    new GeneratedValue(expression, exampleValue)
  }

  @Override
  @SuppressWarnings('UnnecessaryOverridingMethod')
  def call(@DelegatesTo(value = PactBuilder, strategy = Closure.DELEGATE_FIRST) Closure closure) {
    super.build(closure)
  }

  @Override
  @SuppressWarnings('UnnecessaryOverridingMethod')
  def build(@DelegatesTo(value = PactBuilder, strategy = Closure.DELEGATE_FIRST) Closure closure) {
    super.build(closure)
  }
}
