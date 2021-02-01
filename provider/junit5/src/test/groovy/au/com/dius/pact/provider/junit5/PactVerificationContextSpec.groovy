package au.com.dius.pact.provider.junit5

import io.pact.core.model.Interaction
import io.pact.core.model.Request
import io.pact.core.model.RequestResponseInteraction
import io.pact.core.model.Response
import io.pact.core.support.expressions.ValueResolver
import au.com.dius.pact.provider.ConsumerInfo
import au.com.dius.pact.provider.IConsumerInfo
import au.com.dius.pact.provider.IProviderInfo
import au.com.dius.pact.provider.IProviderVerifier
import au.com.dius.pact.provider.VerificationFailureType
import au.com.dius.pact.provider.VerificationResult
import org.junit.jupiter.api.extension.ExtensionContext
import spock.lang.Specification

class PactVerificationContextSpec extends Specification {

  @SuppressWarnings('UnnecessaryGetter')
  def 'sets the test result to an error result if the test fails with an exception'() {
    given:
    PactVerificationContext context
    ExtensionContext.Store store = Stub {
      get(_) >> { args ->
        if (args[0] == 'interactionContext') {
          context
        }
      }
    }
    ExtensionContext extContext = Stub {
      getStore(_) >> store
    }
    TestTarget target = Stub {
      executeInteraction(_, _) >> { throw new IOException('Boom!') }
    }
    IProviderVerifier verifier = Stub()
    ValueResolver valueResolver = Stub()
    IProviderInfo provider = Stub {
      getName() >> 'Stub'
    }
    IConsumerInfo consumer = new ConsumerInfo('Test')
    Interaction interaction = new RequestResponseInteraction('Test Interaction', [], new Request(),
      new Response(), '12345')
    List<VerificationResult> testResults = []

    context = new PactVerificationContext(store, extContext, target, verifier, valueResolver,
      provider, consumer, interaction, testResults)

    when:
    context.verifyInteraction()

    then:
    thrown(AssertionError)
    context.testExecutionResult[0] instanceof VerificationResult.Failed
    context.testExecutionResult[0].description == 'Request to provider failed with an exception'
    context.testExecutionResult[0].failures.size() == 1
    context.testExecutionResult[0].failures['12345'][0] instanceof VerificationFailureType.ExceptionFailure
  }

  @SuppressWarnings('UnnecessaryGetter')
  def 'only throw an exception if there are non-pending failures'() {
    given:
    PactVerificationContext context
    ExtensionContext.Store store = Stub {
      get(_) >> { args ->
        if (args[0] == 'interactionContext') {
          context
        }
      }
    }
    ExtensionContext extContext = Stub {
      getStore(_) >> store
    }
    TestTarget target = Stub {
      executeInteraction(_, _) >> { throw new IOException('Boom!') }
    }
    IProviderVerifier verifier = Stub()
    ValueResolver valueResolver = Stub()
    IProviderInfo provider = Stub {
      getName() >> 'Stub'
    }
    IConsumerInfo consumer = Mock(IConsumerInfo) {
      getName() >> 'test'
      getPending() >> true
    }
    Interaction interaction = new RequestResponseInteraction('Test Interaction', [], new Request(),
      new Response(), '12345')
    List<VerificationResult> testResults = []

    context = new PactVerificationContext(store, extContext, target, verifier, valueResolver,
      provider, consumer, interaction, testResults)

    when:
    context.verifyInteraction()

    then:
    noExceptionThrown()
    context.testExecutionResult[0] instanceof VerificationResult.Failed
    context.testExecutionResult[0].description == 'Request to provider failed with an exception'
    context.testExecutionResult[0].failures.size() == 1
    context.testExecutionResult[0].failures['12345'][0] instanceof VerificationFailureType.ExceptionFailure
  }
}
