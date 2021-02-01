package au.com.dius.pact.provider.junitsupport.loader

import io.pact.core.model.DefaultPactReader
import io.pact.core.model.Pact
import io.pact.core.model.PactReader
import io.pact.core.model.PactSource
import io.pact.core.model.UrlSource
import io.pact.core.model.UrlsSource
import io.pact.core.support.Auth
import io.pact.core.support.expressions.SystemPropertyResolver
import io.pact.core.support.expressions.ValueResolver

/**
 * Implementation of [PactLoader] that downloads pacts from given urls
 */
open class PactUrlLoader(val urls: Array<String>, val authentication: Auth? = null) : PactLoader {
  lateinit var pactSource: UrlsSource
  var pactReader: PactReader = DefaultPactReader
  var resolver: ValueResolver = SystemPropertyResolver()

  constructor(pactUrl: PactUrl) : this(pactUrl.urls, when {
    pactUrl.auth.token.isNotEmpty() -> Auth.BearerAuthentication(pactUrl.auth.token)
    pactUrl.auth.username.isNotEmpty() -> Auth.BasicAuthentication(pactUrl.auth.username,
      pactUrl.auth.password)
    else -> null
  })

  override fun description() = "URL(${urls.contentToString()})"

  override fun load(providerName: String): List<Pact> {
    pactSource = UrlsSource(urls.asList())
    return urls.map { url ->
      val options = mutableMapOf<String, Any>()
      if (authentication != null) {
        options["authentication"] = authentication.resolveProperties(resolver)
      }
      val pact = pactReader.loadPact(UrlSource(url), options)
      pactSource.addPact(url, pact as Pact)
      pact
    }
  }

  override fun getPactSource(): PactSource {
    return pactSource
  }
}
