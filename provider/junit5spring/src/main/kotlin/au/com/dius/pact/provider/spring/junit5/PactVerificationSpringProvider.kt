package au.com.dius.pact.provider.spring.junit5

import io.pact.core.support.expressions.ValueResolver
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.TestContextManager
import org.springframework.test.context.junit.jupiter.SpringExtension

class PactVerificationSpringProvider() : PactVerificationInvocationContextProvider() {

  override fun getValueResolver(context: ExtensionContext): ValueResolver? {
    val store = context.root.getStore(ExtensionContext.Namespace.create(SpringExtension::class.java))
    val testClass = context.requiredTestClass
    val testContextManager = store.getOrComputeIfAbsent(testClass, { TestContextManager(testClass) },
      TestContextManager::class.java)
    val environment = testContextManager.testContext.applicationContext.environment
    return SpringEnvironmentResolver(environment)
  }
}
