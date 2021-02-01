package io.pact.core.matchers

import io.pact.core.model.Response
import mu.KLogging

sealed class ResponseMatch
object FullResponseMatch : ResponseMatch()
data class ResponseMismatch(val mismatches: List<Mismatch>) : ResponseMatch()

object ResponseMatching : KLogging() {

  @JvmStatic
  fun matchRules(expected: Response, actual: Response): ResponseMatch {
    val mismatches = responseMismatches(expected, actual)
    return if (mismatches.isEmpty()) FullResponseMatch
    else ResponseMismatch(mismatches)
  }

  @JvmStatic
  fun responseMismatches(expected: Response, actual: Response): List<Mismatch> {
    val bodyContext = MatchingContext(expected.matchingRules.rulesForCategory("body"), true)
    val headerContext = MatchingContext(expected.matchingRules.rulesForCategory("header"), true)

    val bodyResults = Matching.matchBody(expected, actual, bodyContext)
    val typeResult = if (bodyResults.typeMismatch != null) {
      listOf(bodyResults.typeMismatch)
    } else {
      emptyList()
    }
    return (typeResult + Matching.matchStatus(expected.status, actual.status) +
      Matching.matchHeaders(expected, actual, headerContext).flatMap { it.result } +
      bodyResults.bodyResults.flatMap { it.result }).filterNotNull()
  }
}
