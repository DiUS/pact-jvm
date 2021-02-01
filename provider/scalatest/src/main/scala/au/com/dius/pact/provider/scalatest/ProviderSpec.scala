package au.com.dius.pact.provider.scalatest

import java.io.File
import java.net.URL
import java.util.concurrent.Executors
import io.pact.core.matchers
import io.pact.core.matchers.ResponseMatching
import io.pact.core.model.{Pact => PactForConsumer}
import au.com.dius.pact.provider.scalasupport.HttpClient
import au.com.dius.pact.provider.scalatest.ProviderDsl.defaultPactDirectory
import au.com.dius.pact.provider.scalatest.Tags.ProviderTest
import au.com.dius.pact.provider.{ProviderInfo, ProviderUtils, ProviderVerifier}
import io.pact.core.model.RequestResponseInteraction
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import scala.language.postfixOps

/**
  * Trait to run consumer pacts against the provider
  */
trait ProviderSpec extends FlatSpec with BeforeAndAfterAll with ProviderDsl with Matchers {

  private var handler: Option[ServerStarterWithUrl] = None

  /**
    * Verifies pacts with a given configuration.
    * Every item will be run as a standalone {@link org.scalatest.FlatSpec}
    *
    * @param verificationConfig
    */
  def verify(verificationConfig: VerificationConfig, timeoutSeconds: Duration = 5 seconds): Unit = {

    import verificationConfig.pact._
    import verificationConfig.serverConfig._

    val verifier = new ProviderVerifier
    ProviderUtils.loadPactFiles(new ProviderInfo(provider), new File(uri)).asScala
      .filter(consumer.filter)
      .flatMap(c => verifier.loadPactFileForConsumer(c)
        .asInstanceOf[PactForConsumer]
        .getInteractions.asScala.map(i => (c.getName, i)))
      .foreach { case (consumerName, interaction) =>
        val description = new StringBuilder(s"${interaction.getDescription} for '$consumerName'")
        if (!interaction.getProviderStates.isEmpty) description.append(s" given ${interaction.getProviderStates.get(0).getName}")
        provider should description.toString() taggedAs ProviderTest in {
          startServerWithState(serverStarter, interaction.getProviderStates.asScala.headOption.map(_.getName).getOrElse(""))
          implicit val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
          val requestResponseInteraction = interaction.asInstanceOf[RequestResponseInteraction]
          val request = requestResponseInteraction.getRequest.copy
          handler.foreach(h => request.setPath(s"${h.url.toString}${request.getPath}"))
          val actualResponseFuture = HttpClient.run(request)
          val actualResponse = Await.result(actualResponseFuture, timeoutSeconds)
          if (restartServer) stopServer()
          ResponseMatching.matchRules(requestResponseInteraction.getResponse, actualResponse) shouldBe matchers.FullResponseMatch.INSTANCE
        }
      }
  }

  override def afterAll() = {
    super.afterAll()
    stopServer()
  }

  private def startServerWithState(serverStarter: ServerStarter, state: String) {
    handler = handler.orElse {
      Some(ServerStarterWithUrl(serverStarter))
    }.map { h =>
      h.initState(state)
      h
    }
  }

  private def stopServer() {
    handler.foreach { h =>
      h.stopServer()
      handler = None
    }
  }

  private case class ServerStarterWithUrl(serverStarter: ServerStarter) {
    val url: URL = serverStarter.startServer()

    def initState(state: String) = serverStarter.initState(state)

    def stopServer() = serverStarter.stopServer()
  }

}

/**
  * Convenient abstract class to run pacts from a given directory against a defined provider and consumer.
  * Provider will be restarted and state will be set before every interaction.
  *
  * @param provider
  * @param directory
  * @param consumer
  */
abstract class PactProviderRestartDslSpec(provider: String, directory: String = defaultPactDirectory.directory, consumer: Consumer = ProviderDsl.all) extends ProviderSpec {
  def serverStarter: ServerStarter

  verify(provider complying consumer pacts from(directory) testing (serverStarter) withRestart)
}

/**
  * Convenient abstract class to run pacts from a given directory against a defined provider and consumer.
  * Provider won't be restarted just the state handler server method will be called before every interaction.
  *
  * @param provider
  * @param directory
  * @param consumer
  */
abstract class PactProviderStatefulDslSpec(provider: String, directory: String = defaultPactDirectory.directory, consumer: Consumer = ProviderDsl.all) extends ProviderSpec {
  def serverStarter: ServerStarter

  verify(provider complying consumer pacts from(directory) testing (serverStarter) withoutRestart)
}

