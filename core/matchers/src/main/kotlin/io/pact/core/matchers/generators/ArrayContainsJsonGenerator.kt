package io.pact.core.matchers.generators

import io.pact.core.matchers.JsonBodyMatcher
import io.pact.core.matchers.MatchingContext
import io.pact.core.model.PactSpecVersion
import io.pact.core.model.generators.Generator
import io.pact.core.model.generators.JsonContentTypeHandler
import io.pact.core.model.generators.QueryResult
import io.pact.core.model.matchingrules.MatchingRuleCategory
import io.pact.core.support.json.JsonValue
import mu.KLogging

object ArrayContainsJsonGenerator : KLogging(), Generator {
  override fun generate(context: MutableMap<String, Any>, exampleValue: Any?): Any? {
    return if (exampleValue is JsonValue.Array) {
      for ((index, example) in exampleValue.values.withIndex()) {
        val variant = findMatchingVariant(example, context)
        if (variant != null) {
          logger.debug { "Generating values for variant $variant and value $example" }
          val json = QueryResult(example)
          for ((key, generator) in variant.third) {
            JsonContentTypeHandler.applyKey(json, key, generator, context)
          }
          logger.debug { "Generated value ${json.value}" }
          exampleValue[index] = json.value ?: JsonValue.Null
        }
      }
      exampleValue
    } else {
      logger.error { "ArrayContainsGenerator can only be applied to lists" }
      null
    }
  }

  override fun toMap(pactSpecVersion: PactSpecVersion) = emptyMap<String, Any>()

  private fun findMatchingVariant(
    example: JsonValue,
    context: Map<String, Any?>
  ): Triple<Int, MatchingRuleCategory, Map<String, Generator>>? {
    val variants = context["ArrayContainsVariants"] as List<Triple<Int, MatchingRuleCategory, Map<String, Generator>>>
    return variants.firstOrNull { (index, rules, _) ->
      logger.debug { "Comparing variant $index with value '$example'" }
      val matchingContext = MatchingContext(rules, true)
      val matches = JsonBodyMatcher.compare(listOf("$"), example, example, matchingContext)
      logger.debug { "Comparing variant $index => $matches" }
      matches.flatMap { it.result }.isEmpty()
    }
  }
}
