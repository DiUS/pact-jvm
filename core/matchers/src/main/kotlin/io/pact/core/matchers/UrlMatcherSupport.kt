package io.pact.core.matchers

import io.pact.core.model.matchingrules.RegexMatcher
import java.util.regex.Pattern

data class UrlMatcherSupport(val basePath: String?, val pathFragments: List<Any>) {

  fun getExampleValue(): String {
    val exampleBase = basePath ?: "http://localhost:8080"
    return exampleBase + PATH_SEP + pathFragments.joinToString(separator = PATH_SEP) {
      when (it) {
        is RegexMatcher -> it.example!!
        else -> it.toString()
      }
    }
  }

  fun getRegexExpression(): String {
    return ".*\\/(" + pathFragments.joinToString(separator = "\\/") {
      when (it) {
        is RegexMatcher -> it.regex
        else -> Pattern.quote(it.toString())
      }
    } + ")$"
  }

  companion object {
    const val PATH_SEP = "/"
  }
}
