package io.pact.core.matchers

import io.pact.core.model.HttpPart
import io.pact.core.model.Request
import io.pact.core.model.matchingrules.MatchingRuleCategory
import io.pact.core.model.matchingrules.MatchingRuleGroup
import io.pact.core.model.matchingrules.TypeMatcher
import mu.KLogging

data class MatchingContext(val matchers: MatchingRuleCategory, val allowUnexpectedKeys: Boolean) {
  fun matcherDefined(path: List<String>, pathComparator: Comparator<String> = Comparator.naturalOrder()): Boolean {
    return resolveMatchers(path, pathComparator).isNotEmpty()
  }

  private fun resolveMatchers(path: List<String>, pathComparator: Comparator<String>): MatchingRuleCategory {
    return if (matchers.name == "body")
      matchers.filter { Matchers.matchesPath(it, path) > 0 }
    else if (matchers.name == "header" || matchers.name == "query" || matchers.name == "metadata")
      matchers.filter { key -> path.all { pathComparator.compare(key, it) == 0 } }
    else
      matchers
  }

  fun selectBestMatcher(
    path: List<String>,
    pathComparator: Comparator<String> = Comparator.naturalOrder()
  ): MatchingRuleGroup {
    val matcherCategory = resolveMatchers(path, pathComparator)
    return if (matchers.name == "body")
      matcherCategory.maxBy { a, b ->
        val weightA = Matchers.calculatePathWeight(a, path)
        val weightB = Matchers.calculatePathWeight(b, path)
        when {
          weightA == weightB -> when {
            a.length > b.length -> 1
            a.length < b.length -> -1
            else -> 0
          }
          weightA > weightB -> 1
          else -> -1
        }
      }
    else {
      matcherCategory.matchingRules.values.first()
    }
  }

  fun wildcardMatcherDefined(path: List<String>): Boolean {
    val resolvedMatchers = matchers.filter {
      Matchers.matchesPath(it, path) == path.size
    }
    return resolvedMatchers.matchingRules.keys.any { entry -> entry.endsWith(".*") }
  }

  fun wildcardIndexMatcherDefined(path: List<String>): Boolean {
    val resolvedMatchers = matchers.filter {
      Matchers.matchesPath(it, path) == path.size
    }
    return resolvedMatchers.matchingRules.keys.any { entry -> entry.endsWith("[*]") }
  }

  fun typeMatcherDefined(path: List<String>): Boolean {
    val resolvedMatchers = resolveMatchers(path, Comparator.naturalOrder())
    return resolvedMatchers.allMatchingRules().any { it is TypeMatcher }
  }

  fun <T> matchKeys(
    path: List<String>,
    expectedEntries: Map<String, T>,
    actualEntries: Map<String, T>,
    generateDiff: () -> String
  ): List<BodyItemMatchResult> {
    val p = path + "any"
    return if (!Matchers.wildcardMatchingEnabled() || !wildcardMatcherDefined(p)) {
      val expectedKeys = expectedEntries.keys.sorted()
      val actualKeys = actualEntries.keys
      val actualKeysSorted = actualKeys.sorted()
      val missingKeys = expectedKeys.filter { key -> !actualKeys.contains(key) }
      if (allowUnexpectedKeys && missingKeys.isNotEmpty()) {
        listOf(BodyItemMatchResult(path.joinToString("."), listOf(BodyMismatch(expectedEntries, actualEntries,
          "Actual map is missing the following keys: ${missingKeys.joinToString(", ")}",
          path.joinToString("."), generateDiff()))))
      } else if (!allowUnexpectedKeys && expectedKeys != actualKeysSorted) {
        listOf(BodyItemMatchResult(path.joinToString("."), listOf(BodyMismatch(expectedEntries, actualEntries,
          "Expected a Map with keys $expectedKeys " +
            "but received one with keys $actualKeysSorted",
          path.joinToString("."), generateDiff()))))
      } else {
        emptyList()
      }
    } else {
      emptyList()
    }
  }
}

object Matching : KLogging() {
  private val lowerCaseComparator = Comparator<String> { a, b -> a.toLowerCase().compareTo(b.toLowerCase()) }

  val pathFilter = Regex("http[s]*://([^/]*)")

  @JvmStatic
  fun matchRequestHeaders(expected: Request, actual: Request, context: MatchingContext) =
    matchHeaders(expected.headersWithoutCookie(), actual.headersWithoutCookie(), context)

  @JvmStatic
  fun matchHeaders(expected: HttpPart, actual: HttpPart, context: MatchingContext): List<HeaderMatchResult> =
    matchHeaders(expected.headers, actual.headers, context)

  @JvmStatic
  fun matchHeaders(
    expected: Map<String, List<String>>,
    actual: Map<String, List<String>>,
    context: MatchingContext
  ): List<HeaderMatchResult> = compareHeaders(expected.toSortedMap(lowerCaseComparator),
    actual.toSortedMap(lowerCaseComparator), context)

  fun compareHeaders(
    e: Map<String, List<String>>,
    a: Map<String, List<String>>,
    context: MatchingContext
  ): List<HeaderMatchResult> {
    return e.entries.fold(listOf()) { list, values ->
      if (a.containsKey(values.key)) {
        val actual = a[values.key].orEmpty()
        list + HeaderMatchResult(values.key, values.value.mapIndexed { index, headerValue ->
          HeaderMatcher.compareHeader(values.key, headerValue, actual.getOrElse(index) { "" }, context)
        }.filterNotNull())
      } else {
        list + HeaderMatchResult(values.key,
          listOf(HeaderMismatch(values.key, values.value.joinToString(separator = ", "), "",
          "Expected a header '${values.key}' but was missing")))
      }
    }
  }

  fun matchCookies(expected: List<String>, actual: List<String>, headerContext: MatchingContext) =
    if (expected.all { actual.contains(it) }) null
    else CookieMismatch(expected, actual)

  fun matchMethod(expected: String, actual: String) =
    if (expected.equals(actual, ignoreCase = true)) null
    else MethodMismatch(expected, actual)

  fun matchBody(expected: HttpPart, actual: HttpPart, context: MatchingContext): BodyMatchResult {
    val expectedContentType = expected.determineContentType()
    val actualContentType = actual.determineContentType()
    return if (expectedContentType.getBaseType() == actualContentType.getBaseType()) {
      val matcher = MatchingConfig.lookupBodyMatcher(actualContentType.getBaseType())
      if (matcher != null) {
        logger.debug { "Found a matcher for $actualContentType -> $matcher" }
        matcher.matchBody(expected.body, actual.body, context)
      } else {
        logger.debug { "No matcher for $actualContentType, using equality" }
        when {
          expected.body.isMissing() -> BodyMatchResult(null, emptyList())
          expected.body.isNull() && actual.body.isPresent() -> BodyMatchResult(null,
            listOf(BodyItemMatchResult("$", listOf(BodyMismatch(null, actual.body.unwrap(),
              "Expected an empty body but received '${actual.body.unwrap()}'")))))
          expected.body.isNull() -> BodyMatchResult(null, emptyList())
          actual.body.isMissing() -> BodyMatchResult(null,
            listOf(BodyItemMatchResult("$", listOf(BodyMismatch(expected.body.unwrap(), null,
              "Expected body '${expected.body.unwrap()}' but was missing")))))
          else -> matchBodyContents(expected, actual)
        }
      }
    } else {
      if (expected.body.isMissing() || expected.body.isNull() || expected.body.isEmpty())
        BodyMatchResult(null, emptyList())
      else
        BodyMatchResult(
          BodyTypeMismatch(expectedContentType.getBaseType(), actualContentType.getBaseType()), emptyList())
    }
  }

  fun matchBodyContents(expected: HttpPart, actual: HttpPart): BodyMatchResult {
    val matcher = expected.matchingRules.rulesForCategory("body").matchingRules["$"]
    return when {
      matcher != null && matcher.canMatch(expected.determineContentType()) ->
        BodyMatchResult(null, listOf(BodyItemMatchResult("$",
          domatch(matcher, listOf("$"), expected.body.unwrap(), actual.body.unwrap(), BodyMismatchFactory))))
      expected.body.unwrap().contentEquals(actual.body.unwrap()) -> BodyMatchResult(null, emptyList())
      else -> BodyMatchResult(null, listOf(BodyItemMatchResult("$",
        listOf(BodyMismatch(expected.body.unwrap(), actual.body.unwrap(),
        "Actual body '${actual.body.valueAsString()}' is not equal to the expected body " +
          "'${expected.body.valueAsString()}'")))))
    }
  }

  fun matchPath(expected: Request, actual: Request, context: MatchingContext): PathMismatch? {
    val replacedActual = actual.path.replaceFirst(pathFilter, "")
    return if (context.matcherDefined(emptyList())) {
      val mismatch = Matchers.domatch(context, emptyList(), expected.path, replacedActual, PathMismatchFactory)
      mismatch.firstOrNull()
    } else if (expected.path == replacedActual || replacedActual.matches(Regex(expected.path))) null
    else PathMismatch(expected.path, replacedActual)
  }

  fun matchStatus(expected: Int, actual: Int) = if (expected == actual) null else StatusMismatch(expected, actual)

  fun matchQuery(expected: Request, actual: Request, context: MatchingContext): List<QueryMatchResult> {
    return expected.query.entries.fold(emptyList<QueryMatchResult>()) { acc, entry ->
      when (val value = actual.query[entry.key]) {
        null -> acc +
          QueryMatchResult(entry.key, listOf(QueryMismatch(entry.key, entry.value.joinToString(","), "",
          "Expected query parameter '${entry.key}' but was missing",
          listOf("$", "query", entry.key).joinToString("."))))
        else -> acc +
          QueryMatchResult(entry.key, QueryMatcher.compareQuery(entry.key, entry.value, value, context))
      }
    } + actual.query.entries.fold(emptyList()) { acc, entry ->
      when (expected.query[entry.key]) {
        null -> acc + QueryMatchResult(entry.key, listOf(QueryMismatch(entry.key, "", entry.value.joinToString(","),
          "Unexpected query parameter '${entry.key}' received",
          listOf("$", "query", entry.key).joinToString("."))))
        else -> acc
      }
    }
  }

  @JvmStatic
  fun compareMessageMetadata(
    e: Map<String, Any?>,
    a: Map<String, Any?>,
    context: MatchingContext
  ): List<MetadataMismatch> {
    return e.entries.fold(listOf()) { list, value ->
      if (a.containsKey(value.key)) {
        val actual = a[value.key]
        val compare = MetadataMatcher.compare(value.key, value.value, actual, context)
        if (compare != null) list + compare else list
      } else if (value.key.toLowerCase() != "contenttype" && value.key.toLowerCase() != "content-type") {
        list + MetadataMismatch(value.key, value.value, null,
          "Expected metadata '${value.key}' but was missing")
      } else {
        list
      }
    }
  }
}

data class QueryMatchResult(val key: String, val result: List<QueryMismatch>)
data class HeaderMatchResult(val key: String, val result: List<HeaderMismatch>)
data class BodyItemMatchResult(val key: String, val result: List<BodyMismatch>)
data class BodyMatchResult(val typeMismatch: BodyTypeMismatch?, val bodyResults: List<BodyItemMatchResult>) {
  fun matchedOk() = typeMismatch == null && bodyResults.all { it.result.isEmpty() }

  val mismatches: List<Mismatch>
    get() {
      return if (typeMismatch != null) {
        listOf(typeMismatch)
      } else {
        bodyResults.flatMap { it.result }
      }
    }
}
