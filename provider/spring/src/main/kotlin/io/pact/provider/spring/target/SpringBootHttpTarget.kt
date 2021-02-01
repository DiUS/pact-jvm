package io.pact.provider.spring.target

import io.pact.core.model.Interaction
import io.pact.core.model.PactSource
import io.pact.provider.junit.target.HttpTarget

/**
 * This class sets up an HTTP target configured with the springboot application. Basically, it allows the port
 * to be overridden by the interaction runner which looks up the server
 * port from the spring context.
 */
class SpringBootHttpTarget(override var port: Int = 0) : HttpTarget(port = port) {
  override fun testInteraction(
    consumerName: String,
    interaction: Interaction,
    source: PactSource,
    context: MutableMap<String, Any>
  ) {
    provider.port = port
    super.testInteraction(consumerName, interaction, source, context)
  }
}
