package io.pact.provider.spring.target

import io.pact.core.model.Interaction
import io.pact.core.model.PactSource
import io.pact.core.model.RequestResponseInteraction
import io.pact.provider.spring.WebFluxProviderVerifier
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.server.RouterFunction

class WebFluxTarget(
  runTimes: Int = 1
) : MockTestingTarget(runTimes) {

  var controllers = listOf<Any>()
  var routerFunction: RouterFunction<*>? = null

  override fun testInteraction(
    consumerName: String,
    interaction: Interaction,
    source: PactSource,
    context: MutableMap<String, Any>
  ) {
    doTestInteraction(consumerName, interaction, source) { provider, consumer, verifier, failures ->
      val webClient = routerFunction?.let {
        WebTestClient.bindToRouterFunction(routerFunction).build()
      } ?: WebTestClient.bindToController(*controllers.toTypedArray()).build()
      val webFluxProviderVerifier = verifier as WebFluxProviderVerifier
      webFluxProviderVerifier.verifyResponseFromProvider(
        provider, interaction as RequestResponseInteraction, interaction.description,
        failures, webClient, consumer.pending
      )
    }
  }

  override fun getRequestClass(): Class<*> = WebTestClient.RequestHeadersSpec::class.java

  override fun createProviderVerifier() = WebFluxProviderVerifier()
}
