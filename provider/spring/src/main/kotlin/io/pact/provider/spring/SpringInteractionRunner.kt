package io.pact.provider.spring

import io.pact.core.model.Interaction
import io.pact.core.model.Pact
import io.pact.core.model.PactSource
import io.pact.core.model.UnknownPactSource
import io.pact.provider.junit.InteractionRunner
import io.pact.provider.junitsupport.target.Target
import io.pact.provider.spring.target.SpringBootHttpTarget
import org.junit.After
import org.junit.Before
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import org.junit.runners.model.TestClass
import org.springframework.test.context.TestContextManager
import java.lang.reflect.Method

open class SpringBeforeRunner(
  private val next: Statement,
  private val befores: List<FrameworkMethod>,
  private val testInstance: Any,
  private val testMethod: Method,
  private val testContextManager: TestContextManager
) : Statement() {

  override fun evaluate() {
    testContextManager.beforeTestMethod(testInstance, testMethod)
    for (before in befores) {
      before.invokeExplosively(testInstance)
    }
    next.evaluate()
  }
}

open class SpringAfterRunner(
  private val next: Statement,
  private val afters: List<FrameworkMethod>,
  private val testInstance: Any,
  private val testMethod: Method,
  private val testContextManager: TestContextManager
) : Statement() {

  override fun evaluate() {
    val errors: MutableList<Throwable> = mutableListOf()
    var testException: Throwable? = null
    try {
      next.evaluate()
    } catch (e: Throwable) {
      testException = e
      errors.add(e)
    } finally {
      for (each in afters) {
        try {
          each.invokeExplosively(testInstance)
        } catch (e: Throwable) {
          errors.add(e)
        }
      }
    }

    try {
      testContextManager.afterTestMethod(testInstance, testMethod, testException)
    } catch (ex: Throwable) {
      errors.add(ex)
    }

    MultipleFailureException.assertEmpty(errors)
  }
}

open class SpringInteractionRunner(
  testClass: TestClass,
  pact: Pact,
  pactSource: PactSource?,
  private val testContextManager: TestContextManager
) : InteractionRunner(testClass, pact, pactSource ?: UnknownPactSource) {

  override fun withBefores(interaction: Interaction, testInstance: Any, statement: Statement): Statement {
    val befores = testClass.getAnnotatedMethods(Before::class.java)
    return SpringBeforeRunner(statement, befores, testInstance,
      this.javaClass.getMethod("surrogateTestMethod"), testContextManager)
  }

  override fun withAfters(interaction: Interaction, testInstance: Any, statement: Statement): Statement {
    val afters = testClass.getAnnotatedMethods(After::class.java)
    return SpringAfterRunner(statement, afters, testInstance,
      this.javaClass.getMethod("surrogateTestMethod"), testContextManager)
  }

  override fun createTest(): Any {
    val test = super.createTest()
    testContextManager.prepareTestInstance(test)
    return test
  }

  override fun setupTargetForInteraction(target: Target) {
    super.setupTargetForInteraction(target)

    if (target is SpringBootHttpTarget) {
      val environment = testContextManager.testContext.applicationContext.environment
      val port = environment.getProperty("local.server.port")
      target.port = Integer.parseInt(port)
    }
  }

  open fun surrogateTestMethod() { }
}
