package io.pact.core.matchers

import io.pact.core.model.Interaction
import io.pact.core.model.Request
import io.pact.core.model.RequestResponseInteraction
import io.pact.core.model.Response
import mu.KLogging

sealed class RequestMatch {
  private val score: Int
    get() {
      return when (this) {
        is FullRequestMatch -> this.calculateScore()
        is PartialRequestMatch -> this.calculateScore()
        else -> 0
      }
    }

  /**
   * Take the first total match, or merge partial matches, or take the best available.
   */
  fun merge(other: RequestMatch): RequestMatch = when {
    this is FullRequestMatch && other is FullRequestMatch -> if (this.score >= other.score) this
      else other
    this is FullRequestMatch -> this
    other is FullRequestMatch -> other
    this is PartialRequestMatch && other is PartialRequestMatch -> if (this.score >= other.score) this
      else other
    this is PartialRequestMatch -> this
    else -> other
  }
}

data class FullRequestMatch(val interaction: Interaction, val result: RequestMatchResult) : RequestMatch() {
  fun calculateScore() = result.calculateScore()
}

data class PartialRequestMatch(val problems: Map<Interaction, RequestMatchResult>) : RequestMatch() {
  fun description(): String {
    var s = ""
    for (problem in problems) {
      s += problem.key.description + ":\n"
      for (mismatch in problem.value.mismatches) {
        s += "    " + mismatch.description() + "\n"
      }
    }
    return s
  }

  fun calculateScore() = problems.values.map { it.calculateScore() }.max() ?: 0
}

object RequestMismatch : RequestMatch()

class RequestMatching(private val expectedInteractions: List<RequestResponseInteraction>) {

    fun matchInteraction(actual: Request): RequestMatch {
      val matches = expectedInteractions.map { compareRequest(it, actual) }
      return if (matches.isEmpty())
        RequestMismatch
      else
        matches.reduce { acc, match -> acc.merge(match) }
    }

    fun findResponse(actual: Request): Response? {
      val match = matchInteraction(actual)
      return when (match) {
        is FullRequestMatch -> (match.interaction as RequestResponseInteraction).response
        else -> null
      }
    }

  companion object : KLogging() {
    private fun decideRequestMatch(expected: RequestResponseInteraction, result: RequestMatchResult) =
      when {
        result.matchedOk() -> FullRequestMatch(expected, result)
        result.matchedMethodAndPath() -> PartialRequestMatch(mapOf(expected to result))
        else -> RequestMismatch
      }

    fun compareRequest(expected: RequestResponseInteraction, actual: Request): RequestMatch {
        val mismatches = requestMismatches(expected.request, actual)
        logger.debug { "Request mismatch: $mismatches" }
        return decideRequestMatch(expected, mismatches)
    }

    @JvmStatic
    fun requestMismatches(expected: Request, actual: Request): RequestMatchResult {
      logger.debug { "comparing to expected request: \n$expected" }

      val pathContext = MatchingContext(expected.matchingRules.rulesForCategory("path"), false)
      val bodyContext = MatchingContext(expected.matchingRules.rulesForCategory("body"), false)
      val queryContext = MatchingContext(expected.matchingRules.rulesForCategory("query"), false)
      val headerContext = MatchingContext(expected.matchingRules.rulesForCategory("header"), false)

      return RequestMatchResult(Matching.matchMethod(expected.method, actual.method),
        Matching.matchPath(expected, actual, pathContext),
        Matching.matchQuery(expected, actual, queryContext),
        Matching.matchCookies(expected.cookie(), actual.cookie(), headerContext),
        Matching.matchRequestHeaders(expected, actual, headerContext),
        Matching.matchBody(expected, actual, bodyContext))
    }
  }
}
