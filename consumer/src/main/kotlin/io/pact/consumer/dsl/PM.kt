package io.pact.consumer.dsl

import io.pact.core.model.matchingrules.DateMatcher
import io.pact.core.model.matchingrules.IncludeMatcher
import io.pact.core.model.matchingrules.NullMatcher
import io.pact.core.model.matchingrules.NumberTypeMatcher
import io.pact.core.model.matchingrules.RegexMatcher
import io.pact.core.model.matchingrules.TimeMatcher
import io.pact.core.model.matchingrules.TimestampMatcher
import io.pact.core.model.matchingrules.TypeMatcher

/**
 * Pact Matcher functions for 'and' and 'or'
 */

object PM {

  /**
   * Attribute that can be any string
   */
  @JvmStatic
  fun stringType() = TypeMatcher

  /**
   * Attribute that can be any number
   */
  @JvmStatic
  fun numberType() = NumberTypeMatcher(NumberTypeMatcher.NumberType.NUMBER)

  /**
   * Attribute that must be an integer
   */
  @JvmStatic
  fun integerType() = NumberTypeMatcher(NumberTypeMatcher.NumberType.INTEGER)

  /**
   * Attribute that must be a decimal value
   */
  @JvmStatic
  fun decimalType() = NumberTypeMatcher(NumberTypeMatcher.NumberType.DECIMAL)

  /**
   * Attribute that must be a boolean
   */
  @JvmStatic
  fun booleanType() = TypeMatcher

  /**
   * Attribute that must match the regular expression
   * @param regex regular expression
   */
  @JvmStatic
  @JvmOverloads
  fun stringMatcher(regex: String, example: String? = null) = RegexMatcher(regex, example)

  /**
   * Attribute that must be an ISO formatted timestamp
   */
  @JvmStatic
  fun timestamp() = TimestampMatcher()

  /**
   * Attribute that must match the given timestamp format
   * @param format timestamp format
   */
  @JvmStatic
  fun timestamp(format: String) = TimestampMatcher(format)

  /**
   * Attribute that must be formatted as an ISO date
   */
  @JvmStatic
  fun date() = DateMatcher()

  /**
   * Attribute that must match the provided date format
   * @param format date format to match
   */
  @JvmStatic
  fun date(format: String) = DateMatcher(format)

  /**
   * Attribute that must be an ISO formatted time
   */
  @JvmStatic
  fun time() = TimeMatcher()

  /**
   * Attribute that must match the given time format
   * @param format time format to match
   */
  @JvmStatic
  fun time(format: String) = TimeMatcher(format)

  /**
   * Attribute that must be an IP4 address
   */
  @JvmStatic
  fun ipAddress() = RegexMatcher("(\\d{1,3}\\.)+\\d{1,3}")

  /**
   * Attribute that must be a numeric identifier
   */
  @JvmStatic
  fun id() = TypeMatcher

  /**
   * Attribute that must be encoded as a hexadecimal value
   */
  @JvmStatic
  fun hexValue() = RegexMatcher("[0-9a-fA-F]+")

  /**
   * Attribute that must be encoded as an UUID
   */
  @JvmStatic
  fun uuid() = RegexMatcher("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

  /**
   * Matches a null value
   */
  @JvmStatic
  fun nullValue() = NullMatcher

  /**
   * Attribute that must include the provided string value
   * @param value Value that must be included
   */
  @JvmStatic
  fun includesStr(value: String) = IncludeMatcher(value)
}
