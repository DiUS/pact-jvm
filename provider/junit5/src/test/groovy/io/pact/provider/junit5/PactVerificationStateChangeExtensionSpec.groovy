package io.pact.provider.junit5

import io.pact.core.model.Consumer
import io.pact.core.model.DirectorySource
import io.pact.core.model.Interaction
import io.pact.core.model.PactSource
import io.pact.core.model.Provider
import io.pact.core.model.ProviderState
import io.pact.core.model.RequestResponseInteraction
import io.pact.core.model.RequestResponsePact
import io.pact.core.support.expressions.ValueResolver
import io.pact.provider.IConsumerInfo
import io.pact.provider.IProviderInfo
import io.pact.provider.IProviderVerifier
import io.pact.provider.TestResultAccumulator
import io.pact.provider.VerificationResult
import io.pact.provider.junitsupport.MissingStateChangeMethod
import io.pact.provider.junitsupport.State
import io.pact.provider.junitsupport.StateChangeAction
import org.junit.jupiter.api.extension.ExtensionContext
import spock.lang.Specification
import spock.lang.Unroll

class PactVerificationStateChangeExtensionSpec extends Specification {

  private PactVerificationStateChangeExtension verificationExtension
  Interaction interaction
  private TestResultAccumulator testResultAcc
  RequestResponsePact pact
  private PactVerificationContext pactContext
  private ExtensionContext testContext
  private ExtensionContext.Store store
  private IProviderInfo provider
  private IConsumerInfo consumer
  private PactSource pactSource

  static class TestClass {

    boolean stateCalled = false
    boolean state2Called = false
    boolean state2TeardownCalled = false
    def state3Called = null

    @State('Test 1')
    void state1() {
      stateCalled = true
    }

    @State(['State 2', 'Test 2'])
    void state2() {
      state2Called = true
    }

    @State(value = ['State 2', 'Test 2'], action = StateChangeAction.TEARDOWN)
    void state2Teardown() {
      state2TeardownCalled = true
    }

    @State(['Test 2'])
    void state3(Map params) {
      state3Called = params
    }
  }

  private TestClass testInstance

  def setup() {
    interaction = new RequestResponseInteraction('test')
    pact = new RequestResponsePact(new Provider(), new Consumer(), [ interaction ])
    testResultAcc = Mock(TestResultAccumulator)
    pactSource = new DirectorySource('/tmp' as File)
    verificationExtension = new PactVerificationStateChangeExtension(interaction, pactSource)
    testInstance = new TestClass()
    testContext = [
      'getTestClass': { Optional.of(TestClass) },
      'getTestInstance': { Optional.of(testInstance) }
    ] as ExtensionContext
    store = [:] as ExtensionContext.Store
    provider = Mock()
    consumer = Mock()
    pactContext = new PactVerificationContext(store, testContext, provider, consumer, interaction)
  }

  @Unroll
  def 'throws an exception if it does not find a state change method for the provider state'() {
    given:
    def state = new ProviderState('test state')

    when:
    verificationExtension.invokeStateChangeMethods(testContext, pactContext, [state], StateChangeAction.SETUP)

    then:
    thrown(MissingStateChangeMethod)

    where:

    testClass << [PactVerificationStateChangeExtensionSpec, TestClass]
  }

  def 'invokes the state change method for the provider state'() {
    given:
    def state = new ProviderState('Test 2', [a: 'A', b: 'B'])

    when:
    testInstance.state2Called = false
    testInstance.state2TeardownCalled = false
    testInstance.state3Called = null
    verificationExtension.invokeStateChangeMethods(testContext, pactContext, [state], StateChangeAction.SETUP)

    then:
    testInstance.state2Called
    testInstance.state3Called == state.params
    !testInstance.state2TeardownCalled
  }

  @SuppressWarnings('ClosureAsLastMethodParameter')
  def 'marks the test as failed if the provider state callback fails'() {
    given:
    def state = new ProviderState('test state')
    def interaction = new RequestResponseInteraction('test', [ state ])
    def store = Mock(ExtensionContext.Store)
    def context = Mock(ExtensionContext) {
      getStore(_) >> store
      getRequiredTestClass() >> TestClass
      getRequiredTestInstance() >> testInstance
    }
    def target = Mock(TestTarget)
    IProviderVerifier verifier = Mock()
    ValueResolver resolver = Mock()
    def verificationContext = new PactVerificationContext(store, context, target, verifier, resolver, provider,
      consumer, interaction, [])
    store.get(_) >> verificationContext
    verificationExtension = new PactVerificationStateChangeExtension(interaction, pactSource)

    when:
    verificationExtension.beforeTestExecution(context)

    then:
    thrown(AssertionError)
    verificationContext.testExecutionResult[0] instanceof VerificationResult.Failed
  }
}
