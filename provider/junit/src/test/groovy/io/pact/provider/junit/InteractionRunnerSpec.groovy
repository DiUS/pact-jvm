package io.pact.provider.junit

import io.pact.core.model.Consumer
import io.pact.core.model.FilteredPact
import io.pact.core.model.Provider
import io.pact.core.model.ProviderState
import io.pact.core.model.Request
import io.pact.core.model.RequestResponseInteraction
import io.pact.core.model.RequestResponsePact
import io.pact.core.model.Response
import io.pact.core.model.UnknownPactSource
import io.pact.provider.DefaultTestResultAccumulator
import io.pact.provider.TestResultAccumulator
import io.pact.provider.VerificationReporter
import io.pact.provider.junit.target.HttpTarget
import io.pact.provider.junitsupport.target.Target
import io.pact.provider.junitsupport.target.TestTarget
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.TestClass
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class InteractionRunnerSpec extends Specification {

  @SuppressWarnings('PublicInstanceField')
  class InteractionRunnerTestClass {
    @TestTarget
    public final Target target = new HttpTarget(8332)
  }

  private clazz
  private reporter
  private TestResultAccumulator testResultAccumulator

  def setup() {
    clazz = new TestClass(InteractionRunnerTestClass)
    reporter = Mock(VerificationReporter)
    testResultAccumulator = Mock(TestResultAccumulator)
  }

  def 'publish a failed verification result if any before step fails'() {
    given:
    def interaction1 = new RequestResponseInteraction('Interaction 1',
            [ new ProviderState('Test State') ], new Request(), new Response())
    def interaction2 = new RequestResponseInteraction('Interaction 2', [], new Request(), new Response())
    def pact = new RequestResponsePact(new Provider(), new Consumer(), [ interaction1, interaction2 ])

    def runner = new InteractionRunner(clazz, pact, UnknownPactSource.INSTANCE)
    runner.testResultAccumulator = testResultAccumulator

    when:
    runner.run([:] as RunNotifier)

    then:
    2 * testResultAccumulator.updateTestResult(pact, _, _, _, _)
  }

  @RestoreSystemProperties
  def 'provider version trims -SNAPSHOT'() {
    given:
    System.setProperty('pact.provider.version', '1.0.0-SNAPSHOT-wn23jhd')
    def interaction1 = new RequestResponseInteraction('Interaction 1', [], new Request(), new Response())
    def pact = new RequestResponsePact(new Provider(), new Consumer(), [ interaction1 ])

    def filteredPact = new FilteredPact(pact, { it.description == 'Interaction 1' })
    def runner = new InteractionRunner(clazz, filteredPact, UnknownPactSource.INSTANCE)

    // Property true
    when:
    System.setProperty('pact.provider.version.trimSnapshot', 'true')
    def providerVersion = runner.providerVersion()

    then:
    providerVersion == '1.0.0-wn23jhd'

    // Property false
    when:
    System.setProperty('pact.provider.version.trimSnapshot', 'false')
    providerVersion = runner.providerVersion()

    then:
    providerVersion == '1.0.0-SNAPSHOT-wn23jhd'

    // Property unexpected value
    when:
    System.setProperty('pact.provider.version.trimSnapshot', 'erwf')
    providerVersion = runner.providerVersion()

    then:
    providerVersion == '1.0.0-SNAPSHOT-wn23jhd'

    // Property not present
    when:
    System.clearProperty('pact.provider.version.trimSnapshot')
    providerVersion = runner.providerVersion()

    then:
    providerVersion == '1.0.0-SNAPSHOT-wn23jhd'
  }

  @RestoreSystemProperties
  def 'updateTestResult - if FilteredPact and not all interactions verified then no call on verificationReporter'() {
    given:
    def interaction1 = new RequestResponseInteraction('interaction1', [], new Request(), new Response())
    def interaction2 = new RequestResponseInteraction('interaction2', [], new Request(), new Response())
    def pact = new RequestResponsePact(new Provider(), new Consumer(), [ interaction1, interaction2 ])
    def notifier = Mock(RunNotifier)
    def filteredPact = new FilteredPact(pact, { it.description == 'interaction1' })
    def testResultAccumulator = DefaultTestResultAccumulator.INSTANCE
    testResultAccumulator.verificationReporter = Mock(VerificationReporter) {
      publishingResultsDisabled(_) >> false
    }
    def runner = new InteractionRunner(clazz, filteredPact, UnknownPactSource.INSTANCE)

    when:
    runner.run(notifier)

    then:
    0 * testResultAccumulator.verificationReporter.reportResults(_, _, _, _)
  }

  @RestoreSystemProperties
  @SuppressWarnings('ClosureAsLastMethodParameter')
  def 'If interaction is excluded via properties than it should be marked as ignored'() {
    given:
    System.properties.setProperty('pact.filter.description', 'interaction1')
    def interaction1 = new RequestResponseInteraction('interaction1', [], new Request(), new Response())
    def interaction2 = new RequestResponseInteraction('interaction2', [], new Request(), new Response())
    def pact = new RequestResponsePact(new Provider(), new Consumer(), [ interaction1, interaction2 ])
    def notifier = Mock(RunNotifier)
    def testResultAccumulator = DefaultTestResultAccumulator.INSTANCE
    testResultAccumulator.verificationReporter = Mock(VerificationReporter) {
      publishingResultsDisabled(_) >> false
    }
    def runner = new InteractionRunner(clazz, pact, UnknownPactSource.INSTANCE)

    when:
    runner.run(notifier)

    then:
    1 * notifier.fireTestStarted({ it.displayName.startsWith('consumer - Upon interaction1') })
    0 * notifier.fireTestStarted({ it.displayName.startsWith('consumer - Upon interaction2') })
    0 * notifier.fireTestIgnored({ it.displayName.startsWith('consumer - Upon interaction1') })
    1 * notifier.fireTestIgnored({ it.displayName.startsWith('consumer - Upon interaction2') })
  }
}
