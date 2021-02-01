package io.pact.core.support

import io.pact.core.support.expressions.DataType
import io.pact.core.support.expressions.ExpressionParser.parseExpression
import io.pact.core.support.expressions.ValueResolver
import mu.KLogging
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.SystemDefaultCredentialsProvider
import org.apache.http.message.BasicHeader
import java.net.URI

sealed class Auth {
  data class BasicAuthentication(val username: String, val password: String) : Auth()
  data class BearerAuthentication(val token: String) : Auth()

  fun resolveProperties(resolver: ValueResolver): Auth {
    return when (this) {
      is BasicAuthentication -> BasicAuthentication(parseExpression(this.username, DataType.RAW, resolver).toString(),
          parseExpression(this.password, DataType.RAW, resolver).toString())
      is BearerAuthentication -> BearerAuthentication(parseExpression(this.token, DataType.RAW, resolver).toString())
    }
  }
}

/**
 * HTTP client support functions
 */
object HttpClient : KLogging() {

  /**
   * Creates a new HTTP client
   */
  fun newHttpClient(
    authentication: Any?,
    uri: URI,
    maxPublishRetries: Int = 5,
    publishRetryInterval: Int = 3000
  ): Pair<CloseableHttpClient, CredentialsProvider?> {
    val retryStrategy = CustomServiceUnavailableRetryStrategy(maxPublishRetries, publishRetryInterval)
    val builder = HttpClients.custom().useSystemProperties().setServiceUnavailableRetryStrategy(retryStrategy)

    val defaultHeaders = mutableMapOf<String, String>()
    val credsProvider = when (authentication) {
      is Auth -> {
        when (authentication) {
          is Auth.BasicAuthentication -> basicAuth(uri, authentication.username, authentication.password, builder)
          is Auth.BearerAuthentication -> {
            defaultHeaders["Authorization"] = "Bearer " + authentication.token
            SystemDefaultCredentialsProvider()
          }
        }
      }
      is List<*> -> {
        when (val scheme = authentication.first().toString().toLowerCase()) {
          "basic" -> {
            if (authentication.size > 2) {
              basicAuth(uri, authentication[1].toString(), authentication[2].toString(), builder)
            } else {
              logger.warn { "Basic authentication requires a username and password, ignoring." }
              SystemDefaultCredentialsProvider()
            }
          }
          "bearer" -> {
            if (authentication.size > 1) {
              defaultHeaders["Authorization"] = "Bearer " + authentication[1].toString()
            } else {
              logger.warn { "Bearer token authentication requires a token, ignoring." }
            }
            SystemDefaultCredentialsProvider()
          }
          else -> {
            logger.warn { "HTTP client Only supports basic and bearer token authentication, got '$scheme', ignoring." }
            SystemDefaultCredentialsProvider()
          }
        }
      }
      else -> SystemDefaultCredentialsProvider()
    }

    builder.setDefaultHeaders(defaultHeaders.map { BasicHeader(it.key, it.value) })
    return builder.build() to credsProvider
  }

  private fun basicAuth(
    uri: URI,
    username: String,
    password: String,
    builder: HttpClientBuilder
  ): CredentialsProvider {
    val credsProvider = BasicCredentialsProvider()
    credsProvider.setCredentials(AuthScope(uri.host, uri.port),
      UsernamePasswordCredentials(username, password))
    builder.setDefaultCredentialsProvider(credsProvider)
    return credsProvider
  }
}
