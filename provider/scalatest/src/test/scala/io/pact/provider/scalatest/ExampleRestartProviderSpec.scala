package io.pact.provider.scalatest

import org.junit.runner.RunWith
import org.scalatest.Ignore
import org.scalatestplus.junit.JUnitRunner

/**
  * Provider will be tested against all the defined consumers in the configured default directory.
  * Before each and every interactions the tested provider will be restarted.
  * A freshly started provider will be initialised with the state before verification take place.
  */
@RunWith(classOf[JUnitRunner])
@Ignore
class ExampleRestartProviderSpec extends PactProviderRestartDslSpec("test_provider") {

  lazy val serverStarter: ServerStarter = new ProviderServerStarter
}
