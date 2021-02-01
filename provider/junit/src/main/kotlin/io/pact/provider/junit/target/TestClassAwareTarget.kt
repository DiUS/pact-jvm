package io.pact.provider.junit.target

import io.pact.provider.junitsupport.target.Target
import org.junit.runners.model.TestClass

/**
 * Interface to target implementations that require more information from the test class (like annotated methods)
 */
interface TestClassAwareTarget : Target {
  fun setTestClass(testClass: TestClass, testTarget: Any)
}
