package io.pact.provider.junit

import io.pact.core.model.ProviderState
import io.pact.provider.IProviderVerifier
import io.pact.provider.junitsupport.State
import io.pact.provider.junitsupport.StateChangeAction
import kotlin.Pair
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import spock.lang.Specification

import java.util.function.Supplier

@SuppressWarnings(['ThrowRuntimeException', 'UnnecessaryParenthesesForMethodCallWithClosure'])
class RunStateChangesSpec extends Specification {

  private Statement next
  private ProviderState providerState
  private Map testContext
  private List<Pair<FrameworkMethod, State>> methods
  private List<Supplier> stateChangeHandlers
  private IProviderVerifier verifier

  class TestTarget {
    boolean called = false
    boolean teardownCalled = false

    @State('Test State')
    void stateChange() {
      called = true
    }

    @State(value = 'Test State', action = StateChangeAction.TEARDOWN)
    void stateChangeTeardown() {
      teardownCalled = true
    }
  }

  private TestTarget target

  def setup() {
    providerState = new ProviderState('Test State')
    testContext = [:]
    next = Mock()
    methods = [
      new Pair(new FrameworkMethod(TestTarget.getDeclaredMethod('stateChange')),
        TestTarget.getDeclaredMethod('stateChange').getAnnotation(State))
    ]
    stateChangeHandlers = [
      { target } as Supplier
    ]
    target = Spy(TestTarget)
    verifier = Mock()
  }

  def 'invokes the state change method before the next statement'() {
    when:
    new RunStateChanges(next, methods, stateChangeHandlers, providerState, testContext, verifier).evaluate()

    then:
    1 * next.evaluate()
    1 * target.stateChange()
    0 * target.stateChangeTeardown()
  }

  def 'invokes the state change teardown method after the next statement'() {
    given:
    methods << new Pair(new FrameworkMethod(TestTarget.getDeclaredMethod('stateChangeTeardown')),
      TestTarget.getDeclaredMethod('stateChangeTeardown').getAnnotation(State))

    when:
    new RunStateChanges(next, methods, stateChangeHandlers, providerState, testContext, verifier).evaluate()

    then:
    1 * next.evaluate()
    1 * target.stateChange()

    then:
    1 * target.stateChangeTeardown()
  }

  def 'still invokes the state change teardown method if the the next statement fails'() {
    given:
    methods << new Pair(new FrameworkMethod(TestTarget.getDeclaredMethod('stateChangeTeardown')),
      TestTarget.getDeclaredMethod('stateChangeTeardown').getAnnotation(State))
    next = Mock() {
      evaluate() >> { throw new RuntimeException('Boom') }
    }

    when:
    new RunStateChanges(next, methods, stateChangeHandlers, providerState, testContext, verifier).evaluate()

    then:
    1 * target.stateChange()
    thrown(RuntimeException)

    then:
    1 * target.stateChangeTeardown()
  }

}
