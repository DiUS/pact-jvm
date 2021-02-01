package io.pact.core.model

import io.pact.core.model.messaging.MessagePact
import io.pact.core.support.Json
import io.pact.core.support.json.JsonValue
import com.github.michaelbull.result.Result

/**
 * Pact Provider
 */
data class Provider @JvmOverloads constructor (val name: String = "provider") {
  companion object {
    @JvmStatic
    fun fromJson(json: JsonValue): Provider {
      if (json is JsonValue.Object && json.has("name") && json["name"] is JsonValue.StringValue) {
        val name = Json.toString(json["name"])
        return Provider(if (name.isEmpty()) "provider" else name)
      }
      return Provider("provider")
    }
  }
}

/**
 * Pact Consumer
 */
data class Consumer @JvmOverloads constructor (val name: String = "consumer") {
  companion object {
    @JvmStatic
    fun fromJson(json: JsonValue): Consumer {
      if (json is JsonValue.Object && json.has("name") && json["name"] is JsonValue.StringValue) {
        val name = Json.toString(json["name"])
        return Consumer(if (name.isEmpty()) "consumer" else name)
      }
      return Consumer("consumer")
    }
  }
}

/**
 * Interface to an interaction between a consumer and a provider
 */
interface Interaction {
  /**
   * Interaction description
   */
  val description: String

  /**
   * Returns the provider states for this interaction
   */
  val providerStates: List<ProviderState>

  /**
   * Checks if this interaction conflicts with the other one. Used for merging pact files.
   */
  fun conflictsWith(other: Interaction): Boolean

  /**
   * Converts this interaction to a Map
   */
  fun toMap(pactSpecVersion: PactSpecVersion): Map<String, *>

  /**
   * Generates a unique key for this interaction
   */
  fun uniqueKey(): String

  /**
   * Interaction ID. Will only be populated from pacts loaded from a Pact Broker
   */
  val interactionId: String?

  /** Validates if this Interaction can be used with the provided Pact specification version */
  fun validateForVersion(pactVersion: PactSpecVersion): List<String>

  /** Converts this interaction to a V4 format */
  fun asV4Interaction(): V4Interaction

  /** If this interaction represents an asynchronous message */
  fun isAsynchronousMessage(): Boolean {
    return false
  }
}

/**
 * Interface to a pact
 */
interface Pact {
  /**
   * Returns the provider of the service for the pact
   */
  val provider: Provider

  /**
   * Returns the consumer of the service for the pact
   */
  val consumer: Consumer

  /**
   * Returns all the interactions of the pact
   */
  val interactions: List<Interaction>

  /**
   * The source that this pact was loaded from
   */
  val source: PactSource

  /** Metadata associated with this Pact */
  val metadata: Map<String, Any?>

  /**
   * Returns a pact with the interactions sorted
   */
  fun sortInteractions(): Pact

  /**
   * Returns a Map representation of this pact for the purpose of generating a JSON document.
   */
  fun toMap(pactSpecVersion: PactSpecVersion): Map<String, Any?>

  /**
   * If this pact is compatible with the other pact. Pacts are compatible if they have the
   * same provider and they are the same type
   */
  fun compatibleTo(other: Pact): Boolean

  /**
   * Merges all the interactions into this Pact
   * @param interactions
   */
  fun mergeInteractions(interactions: List<Interaction>): Pact

  /** Validates if this Pact can be used with the provided Pact specification version */
  fun validateForVersion(pactVersion: PactSpecVersion): List<String>

  /** Converts this Pact into a concrete V3 HTTP Pact, if able to */
  fun asRequestResponsePact() : Result<RequestResponsePact, String>

  /** Converts this Pact into a concrete V3 Message Pact, if able to */
  fun asMessagePact() : Result<MessagePact, String>

  /** Converts this Pact into a concrete V4 Pact */
  fun asV4Pact() : Result<V4Pact, String>

  /** Write this Pact out to the provided file for the Pact specification version */
  fun write(pactDir: String, pactSpecVersion: PactSpecVersion) : Result<Int, Throwable>
}
