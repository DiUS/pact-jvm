package au.com.dius.pact.provider.junit5

import io.pact.core.model.Consumer
import io.pact.core.model.FilteredPact
import io.pact.core.model.PactBrokerSource
import io.pact.core.model.Provider
import io.pact.core.model.Request
import io.pact.core.model.RequestResponseInteraction
import io.pact.core.model.RequestResponsePact
import io.pact.core.model.Response
import io.pact.core.support.expressions.ValueResolver
import au.com.dius.pact.provider.IConsumerInfo
import au.com.dius.pact.provider.IProviderInfo
import au.com.dius.pact.provider.IProviderVerifier
import org.junit.jupiter.api.extension.ExtensionContext
import spock.lang.Specification

class PactVerificationExtensionSpec extends Specification {

  def 'updateTestResult uses the original pact when pact is filtered '() {
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

    def interaction1 = new RequestResponseInteraction('interaction1', [], new Request(), new Response())
    def interaction2 = new RequestResponseInteraction('interaction2', [], new Request(), new Response())
    def pact = new RequestResponsePact(new Provider(), new Consumer(), [interaction1, interaction2 ])
    def filteredPact = new FilteredPact(pact, { it.description == 'interaction1' })
    PactBrokerSource pactSource = new PactBrokerSource('localhost', '80', 'http')

    context = new PactVerificationContext(store, extContext, Stub(TestTarget), Stub(IProviderVerifier),
      Stub(ValueResolver), Stub(IProviderInfo), Stub(IConsumerInfo), interaction1, [])

    PactVerificationExtension extension = new PactVerificationExtension(filteredPact, pactSource, interaction1,
      'service', 'consumer')

    when:
    extension.afterTestExecution(extContext)

    then:
    extension.testResultAccumulator.updateTestResult(pact, interaction1, [], pactSource)
  }

    def 'updateTestResult uses the pact itself when pact is not filtered '() {
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

        def interaction1 = new RequestResponseInteraction('interaction1', [], new Request(), new Response())
        def interaction2 = new RequestResponseInteraction('interaction2', [], new Request(), new Response())
        def pact = new RequestResponsePact(new Provider(), new Consumer(), [interaction1, interaction2 ])
        PactBrokerSource pactSource = new PactBrokerSource('localhost', '80', 'http')

        context = new PactVerificationContext(store, extContext, Stub(TestTarget), Stub(IProviderVerifier),
          Stub(ValueResolver), Stub(IProviderInfo), Stub(IConsumerInfo), interaction1, [])

        PactVerificationExtension extension = new PactVerificationExtension(pact, pactSource, interaction1,
          'service', 'consumer')

        when:
        extension.afterTestExecution(extContext)

        then:
        extension.testResultAccumulator.updateTestResult(pact, interaction1, [], pactSource)
    }

}
