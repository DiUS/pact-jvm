package io.pact.provider.readme

import io.dropwizard.testing.ResourceHelpers
import io.dropwizard.testing.junit.DropwizardAppRule
import io.pact.core.model.FileSource
import io.pact.provider.ConsumerInfo
import io.pact.provider.ProviderInfo
import io.pact.provider.ProviderVerifier
import io.pact.provider.VerificationResult
import io.pact.provider.readme.dropwizard.DropwizardConfiguration
import io.pact.provider.readme.dropwizard.TestDropwizardApplication
import org.junit.ClassRule
import org.junit.rules.TestRule
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * This is the example from the README
 */
@SuppressWarnings('EmptyMethod')
class ReadmeExamplePactJVMProviderSpockSpec extends Specification {

  @ClassRule @Shared
  TestRule startServiceRule = new DropwizardAppRule<DropwizardConfiguration>(TestDropwizardApplication,
    ResourceHelpers.resourceFilePath('dropwizard/test-config.yaml'))

  @Shared
  ProviderInfo serviceProvider

  ProviderVerifier verifier

  def setupSpec() {
    serviceProvider = new ProviderInfo('Dropwizard App')
    serviceProvider.protocol = 'http'
    serviceProvider.host = 'localhost'
    serviceProvider.port = 8080
    serviceProvider.path = '/'

    serviceProvider.hasPactWith('zoo_app') { consumer ->
      consumer.pactSource = new FileSource(new File(ResourceHelpers.resourceFilePath(
        'pacts/zoo_app-animal_service.json')))
    }
  }

  def setup() {
    verifier = new ProviderVerifier()
  }

  def cleanup() {
    // cleanup provider state
    // ie. db.truncateAllTables()
  }

  def cleanupSpec() {
    // cleanup provider
  }

  @Unroll
  def "Provider Pact - With Consumer #consumer"() {
    expect:
    verifyConsumerPact(consumer) instanceof VerificationResult.Failed

    where:
    consumer << serviceProvider.consumers
  }

  private VerificationResult verifyConsumerPact(ConsumerInfo consumer) {
    verifier.initialiseReporters(serviceProvider)
    def result = verifier.runVerificationForConsumer([:], serviceProvider, consumer)

    if (result instanceof VerificationResult.Failed) {
      verifier.displayFailures([result])
    }

    result
  }
}
