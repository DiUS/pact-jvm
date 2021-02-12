package io.pact.core.matchers

import com.github.ajalt.mordant.TermColors
import io.pact.core.support.FromJson
import io.pact.core.support.Json
import io.pact.core.support.json.JsonValue

/**
 * Interface to a factory class to create a mismatch
 *
 * @param <Mismatch> Type of mismatch to create
 */
interface MismatchFactory<out M : Mismatch> {
  fun create(expected: Any?, actual: Any?, message: String, path: List<String>): M
}

sealed class Mismatch {
  open fun description() = this::class.java.simpleName + ": " + this.toString()
  open fun description(t: TermColors) = this.description()
  open fun type(): String = this::class.java.simpleName
}

data class StatusMismatch(val expected: Int, val actual: Int) : Mismatch() {
  override fun description() = "expected status of $expected but was $actual"
  override fun description(t: TermColors) =
    "expected status of ${t.bold(expected.toString())} but was ${t.bold(actual.toString())}"
  fun toMap(): Map<String, Any?> {
    return mapOf("mismatch" to description())
  }
  override fun type() = "status"
}

data class BodyTypeMismatch(val expected: String?, val actual: String?) : Mismatch() {
  override fun description() = "Expected a response type of '$expected' " +
    "but the actual type was '$actual'"
  override fun description(t: TermColors) =
    "Expected a response type of ${t.bold("'$expected'")} but the actual type was ${t.bold("'$actual'")}"
  fun toMap(): Map<String, Any?> {
    return mapOf("mismatch" to description())
  }
  override fun type() = "body-content-type"

  companion object: FromJson<BodyTypeMismatch> {
    override fun fromJson(json: JsonValue) = if (json is JsonValue.Object) {
      BodyTypeMismatch(json["expected"].asString(), json["actual"].asString())
    } else {
      null
    }
  }
}

data class CookieMismatch(val expected: List<String>, val actual: List<String>) : Mismatch()

data class PathMismatch @JvmOverloads constructor (
  val expected: String,
  val actual: String,
  val mismatch: String? = null
) : Mismatch() {
  override fun description() = when (mismatch) {
    null -> super.description()
    else -> mismatch
  }
  override fun type() = "path"
}

object PathMismatchFactory : MismatchFactory<PathMismatch> {
  override fun create(expected: Any?, actual: Any?, message: String, path: List<String>) =
    PathMismatch(expected.toString(), actual.toString(), message)
}

data class MethodMismatch(val expected: String, val actual: String) : Mismatch() {
  override fun type() = "method"
}

data class QueryMismatch(
  val queryParameter: String,
  val expected: String,
  val actual: String,
  val mismatch: String? = null,
  val path: String = "/"
) : Mismatch() {
  override fun type() = "query"
}

object QueryMismatchFactory : MismatchFactory<QueryMismatch> {
    override fun create(expected: Any?, actual: Any?, message: String, path: List<String>) =
      QueryMismatch(path.last(), expected.toString(), actual.toString(), message)
}

data class HeaderMismatch(
  val headerKey: String,
  val expected: String,
  val actual: String,
  val mismatch: String
) : Mismatch() {
  val regex = Regex("'[^']*'")
  override fun description() = mismatch
  override fun description(t: TermColors): String {
    return mismatch.replace(regex) { m -> t.bold(m.value) }
  }
  override fun type() = "header"

  fun merge(mismatch: HeaderMismatch): HeaderMismatch {
    return if (this.mismatch.isNotEmpty()) {
      copy(mismatch = this.mismatch + ", " + mismatch.mismatch)
    } else {
      copy(mismatch = mismatch.mismatch)
    }
  }

  fun toMap(): Map<String, Any?> {
    return mapOf("mismatch" to description())
  }
}

object HeaderMismatchFactory : MismatchFactory<HeaderMismatch> {
  override fun create(expected: Any?, actual: Any?, message: String, path: List<String>) =
    HeaderMismatch(path.last(), expected.toString(), actual.toString(), message)
}

data class BodyMismatch @JvmOverloads constructor(
  val expected: Any?,
  val actual: Any?,
  val mismatch: String,
  val path: String = "/",
  val diff: String? = null
) : Mismatch() {
  override fun description() = mismatch
  override fun type() = "body"

  companion object : FromJson<BodyMismatch> {
    override fun fromJson(json: JsonValue) = if (json is JsonValue.Object) {
      BodyMismatch(
        json["expected"].asString(),
        json["actual"].asString(),
        json["mismatch"].asString().orEmpty(),
        json["path"].asString() ?: "/",
        json["diff"].asString()
      )
    } else {
      null
    }
  }
}

object BodyMismatchFactory : MismatchFactory<BodyMismatch> {
  override fun create(expected: Any?, actual: Any?, message: String, path: List<String>) =
    BodyMismatch(expected, actual, message, path.joinToString("."))
}

data class MetadataMismatch(val key: String, val expected: Any?, val actual: Any?, val mismatch: String) : Mismatch() {
  override fun description() = mismatch
  override fun type() = "metadata"

  fun merge(mismatch: MetadataMismatch) = copy(mismatch = this.mismatch + ", " + mismatch.mismatch)
}

object MetadataMismatchFactory : MismatchFactory<MetadataMismatch> {
  override fun create(expected: Any?, actual: Any?, message: String, path: List<String>) =
    MetadataMismatch(path.last(), expected, actual, message)
}
