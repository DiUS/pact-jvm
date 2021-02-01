package io.pact.consumer

import io.pact.core.matchers.BodyMismatch
import io.pact.core.matchers.Mismatch
import io.pact.core.model.Request

sealed class PactVerificationResult {
  open fun getDescription() = toString()

  data class Ok(val result: Any? = null) : PactVerificationResult()

  data class Error(val error: Throwable, val mockServerState: PactVerificationResult) : PactVerificationResult()

  data class PartialMismatch(val mismatches: List<Mismatch>) : PactVerificationResult() {
    override fun getDescription(): String {
      return mismatches.joinToString("\n") {
        when (it) {
          is BodyMismatch -> "${it.type()} - ${it.path}: ${it.description()}"
          else -> "${it.type()} - ${it.description()}"
        }
      }
    }
  }

  data class Mismatches(val mismatches: List<PactVerificationResult>) : PactVerificationResult() {
    override fun getDescription(): String {
      return "The following mismatched requests occurred:\n" +
        mismatches.joinToString("\n", transform = PactVerificationResult::getDescription)
    }
  }

  data class UnexpectedRequest(val request: Request) : PactVerificationResult() {
    override fun getDescription() = "Unexpected Request:\n$request"
  }

  data class ExpectedButNotReceived(val expectedRequests: List<Request>) : PactVerificationResult() {
    override fun getDescription(): String {
      return "The following requests were not received:\n" + expectedRequests.joinToString("\n")
    }
  }
}
