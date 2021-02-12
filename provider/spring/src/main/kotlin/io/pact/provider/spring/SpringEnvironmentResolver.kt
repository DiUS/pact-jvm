package io.pact.provider.spring

import io.pact.core.support.expressions.SystemPropertyResolver
import io.pact.core.support.expressions.ValueResolver
import org.springframework.core.env.Environment

class SpringEnvironmentResolver(private val environment: Environment) : ValueResolver {
  override fun resolveValue(property: String?): String? {
    val tuple = SystemPropertyResolver.PropertyValueTuple(property).invoke()
    return environment.getProperty(tuple.propertyName, tuple.defaultValue)
  }

  override fun resolveValue(property: String?, default: String?): String? {
    return environment.getProperty(property, default)
  }

  override fun propertyDefined(property: String): Boolean {
    val tuple = SystemPropertyResolver.PropertyValueTuple(property).invoke()
    return environment.containsProperty(tuple.propertyName)
  }
}
