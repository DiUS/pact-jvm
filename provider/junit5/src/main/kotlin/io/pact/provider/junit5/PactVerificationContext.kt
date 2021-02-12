package io.pact.provider.junit5

import io.pact.core.model.Interaction
import io.pact.core.model.PactSource
import io.pact.core.model.RequestResponseInteraction
import io.pact.core.model.UnknownPactSource
import io.pact.core.support.expressions.SystemPropertyResolver
import io.pact.core.support.expressions.ValueResolver
import io.pact.provider.IConsumerInfo
import io.pact.provider.IProviderInfo
import io.pact.provider.IProviderVerifier
import io.pact.provider.PactVerification
import io.pact.provider.ProviderVerifier
import io.pact.provider.VerificationFailureType
import io.pact.provider.VerificationResult
import io.pact.provider.junitsupport.TestDescription
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * The instance that holds the context for the test of an interaction. The test target will need to be set on it in
 * the before each phase of the test, and the verifyInteraction method must be called in the test template method.
 */
data class PactVerificationContext @JvmOverloads constructor(
  private val store: ExtensionContext.Store,
  private val context: ExtensionContext,
  var target: TestTarget = HttpTestTarget(port = 8080),
  var verifier: IProviderVerifier? = null,
  var valueResolver: ValueResolver = SystemPropertyResolver,
  var providerInfo: IProviderInfo,
  val consumer: IConsumerInfo,
  val interaction: Interaction,
  var testExecutionResult: MutableList<VerificationResult.Failed> = mutableListOf()
) {
  val stateChangeHandlers: MutableList<Any> = mutableListOf()
  var executionContext: MutableMap<String, Any>? = null

  /**
   * Called to verify the interaction from the test template method.
   *
   * @throws AssertionError Throws an assertion error if the verification fails.
   */
  fun verifyInteraction() {
    val store = context.getStore(namespace)
    val client = store.get("client")
    val request = store.get("request")
    val testContext = store.get("interactionContext") as PactVerificationContext
    try {
      val result = validateTestExecution(client, request, testContext.executionContext ?: mutableMapOf())
        .filterIsInstance<VerificationResult.Failed>()
      this.testExecutionResult.addAll(result)
      if (testExecutionResult.isNotEmpty()) {
        verifier!!.displayFailures(testExecutionResult)
        if (testExecutionResult.any { !it.pending }) {
          val pactSource = consumer.resolvePactSource()
          val source = if (pactSource is PactSource) {
            pactSource
          } else {
            UnknownPactSource
          }
          val description = TestDescription(interaction, source, null, consumer.toPactConsumer())
          throw AssertionError(description.generateDescription() +
            verifier!!.generateErrorStringFromVerificationResult(testExecutionResult))
        }
      }
    } finally {
      verifier!!.finaliseReports()
    }
  }

  private fun validateTestExecution(
    client: Any?,
    request: Any?,
    context: MutableMap<String, Any>
  ): List<VerificationResult> {
    if (providerInfo.verificationType == null || providerInfo.verificationType == PactVerification.REQUEST_RESPONSE) {
      val interactionMessage = "Verifying a pact between ${consumer.name} and ${providerInfo.name}" +
        " - ${interaction.description}"
      return try {
        val reqResInteraction = interaction as RequestResponseInteraction
        val expectedResponse = reqResInteraction.response.generatedResponse(context)
        val actualResponse = target.executeInteraction(client, request)

        listOf(verifier!!.verifyRequestResponsePact(expectedResponse, actualResponse, interactionMessage, mutableMapOf(),
          reqResInteraction.interactionId.orEmpty(), consumer.pending))
      } catch (e: Exception) {
        verifier!!.reporters.forEach {
          it.requestFailed(providerInfo, interaction, interactionMessage, e,
            verifier!!.projectHasProperty.apply(ProviderVerifier.PACT_SHOW_STACKTRACE))
        }
        listOf(VerificationResult.Failed("Request to provider failed with an exception", interactionMessage,
          mapOf(interaction.interactionId.orEmpty() to
            listOf(VerificationFailureType.ExceptionFailure("Request to provider failed with an exception", e))),
          consumer.pending))
      }
    } else {
      return listOf(verifier!!.verifyResponseByInvokingProviderMethods(providerInfo, consumer, interaction,
        interaction.description, mutableMapOf()))
    }
  }

  fun withStateChangeHandlers(vararg stateClasses: Any): PactVerificationContext {
    stateChangeHandlers.addAll(stateClasses)
    return this
  }

  fun addStateChangeHandlers(vararg stateClasses: Any) {
    stateChangeHandlers.addAll(stateClasses)
  }
}
