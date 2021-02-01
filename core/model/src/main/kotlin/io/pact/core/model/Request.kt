package io.pact.core.model

import io.pact.core.model.ContentType.Companion.UNKNOWN
import io.pact.core.model.generators.Category
import io.pact.core.model.generators.GeneratorTestMode
import io.pact.core.model.generators.Generators
import io.pact.core.model.matchingrules.MatchingRules
import io.pact.core.model.matchingrules.MatchingRulesImpl
import io.pact.core.support.Json
import io.pact.core.support.json.JsonValue
import mu.KLogging

/**
 * Request made by a consumer to a provider
 */
class Request @JvmOverloads constructor(
  var method: String = DEFAULT_METHOD,
  var path: String = DEFAULT_PATH,
  var query: MutableMap<String, List<String>> = mutableMapOf(),
  override var headers: MutableMap<String, List<String>> = mutableMapOf(),
  override var body: OptionalBody = OptionalBody.missing(),
  override var matchingRules: MatchingRules = MatchingRulesImpl(),
  override var generators: Generators = Generators()
) : BaseRequest(), Comparable<Request> {

  override fun compareTo(other: Request) = if (equals(other)) 0 else 1

  fun copy() = Request(method, path, query.toMutableMap(), headers.toMutableMap(), body.copy(), matchingRules.copy(),
    generators.copy())

  @JvmOverloads
  fun generatedRequest(
    context: MutableMap<String, Any> = mutableMapOf(),
    mode: GeneratorTestMode = GeneratorTestMode.Provider
  ): Request {
    val r = this.copy()
    val pathGenerators = r.buildGenerators(Category.PATH, context)
    if (pathGenerators.isNotEmpty()) {
      Generators.applyGenerators(pathGenerators, mode) { _, g -> r.path = g.generate(context, r.path).toString() }
    }
    val headerGenerators = r.buildGenerators(Category.HEADER, context)
    if (headerGenerators.isNotEmpty()) {
      Generators.applyGenerators(headerGenerators, mode) { key, g ->
        r.headers[key] = listOf(g.generate(context, r.headers[key]).toString())
      }
    }
    val queryGenerators = r.buildGenerators(Category.QUERY, context)
    if (queryGenerators.isNotEmpty()) {
      Generators.applyGenerators(queryGenerators, mode) { key, g ->
        r.query[key] = r.query.getOrElse(key) { emptyList() }.map { g.generate(context, r.query[key]).toString() }
      }
    }
    if (r.body.isPresent()) {
      val bodyGenerators = r.buildGenerators(Category.BODY, context)
      if (bodyGenerators.isNotEmpty()) {
        r.body = Generators.applyBodyGenerators(bodyGenerators, r.body, determineContentType(), context, mode)
      }
    }
    return r
  }

  override fun toString(): String {
    return "\tmethod: $method\n\tpath: $path\n\tquery: $query\n\theaders: $headers\n\tmatchers: $matchingRules\n\t" +
      "generators: $generators\n\tbody: $body"
  }

  fun headersWithoutCookie(): Map<String, List<String>> {
    return headers.filter { (k, _) -> k.toLowerCase() != COOKIE_KEY }
  }

  fun cookie(): List<String> {
    val cookieEntry = headers.entries.find { (k, _) -> k.toLowerCase() == COOKIE_KEY }
    return if (cookieEntry != null) {
      cookieEntry.value.flatMap {
        it.split(';')
      }.map { it.trim() }
    } else {
      emptyList()
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Request

    if (method != other.method) return false
    if (path != other.path) return false
    if (query != other.query) return false
    if (headers != other.headers) return false
    if (body != other.body) return false
    if (matchingRules != other.matchingRules) return false
    if (generators != other.generators) return false

    return true
  }

  override fun hashCode(): Int {
    var result = method.hashCode()
    result = 31 * result + path.hashCode()
    result = 31 * result + query.hashCode()
    result = 31 * result + headers.hashCode()
    result = 31 * result + body.hashCode()
    result = 31 * result + matchingRules.hashCode()
    result = 31 * result + generators.hashCode()
    return result
  }

  fun asV4Request(): HttpRequest {
    return HttpRequest(method, path, query, headers, body, matchingRules, generators)
  }

  companion object : KLogging() {
    const val COOKIE_KEY = "cookie"
    const val DEFAULT_METHOD = "GET"
    const val DEFAULT_PATH = "/"

    @JvmStatic
    fun fromJson(json: JsonValue.Object): Request {
      val method = if (json.has("method")) Json.toString(json["method"]) else DEFAULT_METHOD
      val path = if (json.has("path")) Json.toString(json["path"]) else DEFAULT_PATH
      val query = parseQueryParametersToMap(json["query"])
      val headers = if (json.has("headers") && json["headers"] is JsonValue.Object) {
        json["headers"].asObject()!!.entries.entries.associate { (key, value) ->
          if (value is JsonValue.Array) {
            key to value.values.map { Json.toString(it) }
          } else {
            key to listOf(Json.toString(value).trim())
          }
        }
      } else {
        emptyMap()
      }

      var contentType = UNKNOWN
      val contentTypeEntry = headers.entries.find { it.key.toUpperCase() == "CONTENT-TYPE" }
      if (contentTypeEntry != null) {
        contentType = ContentType(contentTypeEntry.value.first())
      }

      val body = if (json.has("body")) {
        extractBody(json, contentType)
      } else OptionalBody.missing()
      val matchingRules = if (json.has("matchingRules") && json["matchingRules"] is JsonValue.Object)
        MatchingRulesImpl.fromJson(json["matchingRules"])
        else MatchingRulesImpl()
      val generators = if (json.has("generators") && json["generators"] is JsonValue.Object)
        Generators.fromJson(json["generators"])
        else Generators()
      return Request(method, path, query.toMutableMap(), headers.toMutableMap(), body, matchingRules, generators)
    }
  }
}
