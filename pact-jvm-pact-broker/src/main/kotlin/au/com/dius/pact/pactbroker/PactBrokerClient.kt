package au.com.dius.pact.pactbroker

import au.com.dius.pact.com.github.michaelbull.result.Err
import au.com.dius.pact.com.github.michaelbull.result.Result
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.toJson
import java.net.URLDecoder
import java.util.function.Consumer

/**
 * Wraps the response for a Pact from the broker with the link data associated with the Pact document.
 */
data class PactResponse(val pactFile: Any, val links: Map<String, Map<String, Any>>)

/**
 * Pact broker base class
 */
abstract class PactBrokerClientBase(val pactBrokerUrl: String, val options: Map<String, Any> = mapOf()) {

  protected abstract fun newHalClient(): IHalClient

  /**
   * Publishes the result to the "pb:publish-verification-results" link in the document attributes.
   */
  @JvmOverloads
  open fun publishVerificationResults(
    docAttributes: Map<String, Map<String, Any>>,
    result: Boolean,
    version: String,
    testResults: String? = null,
    buildUrl: String? = null
  ): Result<Boolean, Exception> {
    val halClient = newHalClient()
    val publishLink = docAttributes.mapKeys { it.key.toLowerCase() } ["pb:publish-verification-results"] // ktlint-disable curly-spacing
    return if (publishLink != null) {
      val jsonObject = jsonObject("success" to result, "providerApplicationVersion" to version)
      if (testResults != null) {
        jsonObject.add("testResults", testResults.toJson())
      }
      if (buildUrl != null) {
        jsonObject.add("buildUrl", buildUrl.toJson())
      }
      val lowercaseMap = publishLink.mapKeys { it.key.toLowerCase() }
      if (lowercaseMap.containsKey("href")) {
        halClient.postJson(lowercaseMap["href"].toString(), jsonObject.toString())
      } else {
        Err(RuntimeException("Unable to publish verification results as there is no " +
          "pb:publish-verification-results link"))
      }
    } else {
      Err(RuntimeException("Unable to publish verification results as there is no " +
        "pb:publish-verification-results link"))
    }
  }

  open fun fetchLatestConsumersWithNoTag(provider: String): List<PactBrokerConsumer> {
    return try {
      val halClient = newHalClient()
      val consumers = mutableListOf<PactBrokerConsumer>()
      halClient.navigate(mapOf("provider" to provider), LATEST_PROVIDER_PACTS_WITH_NO_TAG)
        .forAll(PACTS, Consumer { pact ->
          val href = URLDecoder.decode(pact["href"].toString(), UTF8)
          val name = pact["name"].toString()
          if (options.containsKey("authentication")) {
            consumers.add(PactBrokerConsumer(name, href, pactBrokerUrl, options["authentication"] as List<String>))
          } else {
            consumers.add(PactBrokerConsumer(name, href, pactBrokerUrl, emptyList()))
          }
        })
      consumers
    } catch (_: NotFoundHalResponse) {
      // This means the provider is not defined in the broker, so fail gracefully.
      emptyList()
    }
  }

  companion object {
    const val LATEST_PROVIDER_PACTS_WITH_NO_TAG = "pb:latest-untagged-pact-version"
    const val PACTS = "pacts"
    const val UTF8 = "UTF-8"
  }
}
