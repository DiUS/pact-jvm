package au.com.dius.pact.provider

import io.pact.core.matchers.BodyMismatch
import io.pact.core.matchers.HeaderMismatch
import io.pact.core.matchers.MetadataMismatch
import io.pact.core.matchers.Mismatch
import io.pact.core.matchers.QueryMismatch
import io.pact.core.model.Interaction
import io.pact.core.model.Pact
import io.pact.core.pactbroker.TestResult
import io.pact.core.support.isNotEmpty
import com.github.ajalt.mordant.TermColors
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.getError

private fun padLines(str: String, indent: Int): String {
  val pad = " ".repeat(indent)
  return str.split('\n').joinToString("\n") { pad + it }
}

sealed class VerificationFailureType {
  abstract fun description(): String
  abstract fun formatForDisplay(t: TermColors): String
  abstract fun hasException(): Boolean
  abstract fun getException(): Throwable?

  data class MismatchFailure(
    val mismatch: Mismatch,
    val interaction: Interaction? = null,
    val pact: Pact? = null
  ) : VerificationFailureType() {
    override fun description() = formatForDisplay(TermColors())
    override fun formatForDisplay(t: TermColors): String {
      return when (mismatch) {
        is BodyMismatch -> {
          var description = "${mismatch.type()}: ${t.bold(mismatch.path)} ${mismatch.description(t)}"

          if (mismatch.diff.isNotEmpty()) {
            description += "\n\n" + formatDiff(t, mismatch.diff!!) + "\n"
          }

          description
        }
        else -> mismatch.type() + ": " + mismatch.description(t)
      }
    }

    override fun hasException() = false
    override fun getException() = null

    private fun formatDiff(t: TermColors, diff: String): String {
      val pad = " ".repeat(8)
      return diff.split('\n').joinToString("\n") {
        pad + when {
          it.startsWith('-') -> t.red(it)
          it.startsWith('+') -> t.green(it)
          else -> it
        }
      }
    }
  }

  data class ExceptionFailure(val description: String, val e: Throwable) : VerificationFailureType() {
    override fun description() = e.message ?: e.javaClass.name
    override fun formatForDisplay(t: TermColors): String {
      return if (e.message.isNotEmpty()) {
        padLines(e.message!!, 6)
      } else {
        "      ${e.javaClass.name}"
      }
    }

    override fun hasException() = true
    override fun getException() = e
  }

  data class StateChangeFailure(val description: String, val result: StateChangeResult) : VerificationFailureType() {
    override fun description() = formatForDisplay(TermColors())
    override fun formatForDisplay(t: TermColors): String {
      val e = result.stateChangeResult.getError()
      return "State change callback failed with an exception - " + e?.message.toString()
    }

    override fun hasException() = result.stateChangeResult is Err
    override fun getException() = result.stateChangeResult.getError()
  }
}

typealias VerificationFailures = Map<String, List<VerificationFailureType>>

/**
 * Result of verifying an interaction
 */
sealed class VerificationResult {
  /**
   * Result was successful
   */
  data class Ok @JvmOverloads constructor(val interactionIds: Set<String> = emptySet()) : VerificationResult() {

    constructor(interactionId: String?) : this(if (interactionId.isNullOrEmpty())
      emptySet() else setOf(interactionId))

    override fun merge(result: VerificationResult) = when (result) {
      is Ok -> this.copy(interactionIds = interactionIds + result.interactionIds)
      is Failed -> result.merge(this)
    }

    override fun toTestResult() = TestResult.Ok(interactionIds)
  }

  /**
   * Result failed
   */
  data class Failed @JvmOverloads constructor(
    val description: String = "",
    val verificationDescription: String = "",
    val failures: VerificationFailures = mapOf(),
    val pending: Boolean = false,
    @Deprecated("use failures instead")
    var results: List<Map<String, Any?>> = emptyList()
  ) : VerificationResult() {
    override fun merge(result: VerificationResult) = when (result) {
      is Ok -> this.copy(failures = failures + result.interactionIds
        .associateWith {
          (if (failures.containsKey(it)) failures[it] else emptyList<VerificationFailureType>())!!
        })
      is Failed -> Failed(when {
        description.isNotEmpty() && result.description.isNotEmpty() && description != result.description ->
          "$description, ${result.description}"
        description.isNotEmpty() -> description
        else -> result.description
      }, verificationDescription, mergeFailures(failures, result.failures), pending && result.pending)
    }

    private fun mergeFailures(failures: VerificationFailures, other: VerificationFailures): VerificationFailures {
      return (failures.entries + other.entries).groupBy { it.key }
        .mapValues { entry -> entry.value.flatMap { it.value } }
    }

    override fun toTestResult(): TestResult {
      val failures = failures.flatMap { entry ->
        if (entry.value.isNotEmpty()) {
          entry.value.map { failure ->
            val errorMap = when (failure) {
              is VerificationFailureType.ExceptionFailure -> listOf(
                "exception" to failure.getException(), "description" to failure.description
              )
              is VerificationFailureType.StateChangeFailure -> listOf(
                "exception" to failure.getException(), "description" to failure.description
              )
              is VerificationFailureType.MismatchFailure -> listOf(
                "attribute" to failure.mismatch.type(),
                "description" to failure.mismatch.description()
              ) + when (val mismatch = failure.mismatch) {
                is BodyMismatch -> listOf(
                  "identifier" to mismatch.path, "description" to mismatch.mismatch,
                  "diff" to mismatch.diff
                )
                is HeaderMismatch -> listOf("identifier" to mismatch.headerKey, "description" to mismatch.mismatch)
                is QueryMismatch -> listOf("identifier" to mismatch.queryParameter, "description" to mismatch.mismatch)
                is MetadataMismatch -> listOf("identifier" to mismatch.key, "description" to mismatch.mismatch)
                else -> listOf()
              }
            }
            (listOf("interactionId" to entry.key) + errorMap).toMap()
          }
        } else {
          listOf(mapOf("interactionId" to entry.key))
        }
      }
      return TestResult.Failed(failures, description)
    }
  }

  /**
   * Merge this result with the other one, creating a new result
   */
  abstract fun merge(result: VerificationResult): VerificationResult

  /**
   * Convert to a test result
   */
  abstract fun toTestResult(): TestResult
}
