package io.pact.consumer.groovy

import org.junit.Before
import org.junit.Test

@SuppressWarnings('JUnitTestMethodWithoutAssert')
class MatchersTest {

  private Matchers matchers

  @Before
  void setup() {
    matchers = new Matchers()
  }

  @Test(expected = InvalidMatcherException)
  void 'regexp matcher fails if the regular expression does not match the example'() {
    matchers.regexp('[a-z]+', 'aksdfkdshfkjdhf23876r872')
  }

  @Test
  void 'regexp matcher does not fail if the regular expression matches the example'() {
    matchers.regexp('[a-z0-9]+', 'aksdfkdshfkjdhf23876r872')
  }

  @Test(expected = InvalidMatcherException)
  void 'regexp matcher with a pattern fails if the regular expression does not match the example'() {
    matchers.regexp(~/[a-z]+/, 'aksdfkdshfkjdhf23876r872')
  }

  @Test
  void 'regexp matcher with a pattern does not fail if the regular expression matches the example'() {
    matchers.regexp(~/[a-z0-9]+/, 'aksdfkdshfkjdhf23876r872')
  }

  @Test(expected = InvalidMatcherException)
  void 'hexadecimal matcher fails if the the example is not a hexadecimal number'() {
    matchers.hexValue('aksdfkdshfkjdhf23876r872')
  }

  @Test
  void 'hexadecimal matcher does not fail if the the example is a hexadecimal number'() {
    matchers.hexValue('afdfdf23876872')
  }

  @Test(expected = InvalidMatcherException)
  void 'ip address matcher fails if the the example is not an ip address'() {
    matchers.ipAddress('aksdfkdshfkjdhf23876r872')
  }

  @Test
  void 'ip address matcher does not fail if the the example is an ipaddress'() {
    matchers.ipAddress('10.10.100.1')
  }

  @Test(expected = InvalidMatcherException)
  void 'timestamp matcher fails if the the example does not match the given pattern'() {
    matchers.datetime('yyyyMMddhh', '2001101014')
  }

  @Test
  void 'timestamp matcher does not fail if the the example matches the pattern'() {
    matchers.datetime('yyyyMMddHH', '2001101002')
  }

  @Test(expected = InvalidMatcherException)
  void 'date matcher fails if the the example does not match the given pattern'() {
    matchers.date('yyyyMMdd', '20011410')
  }

  @Test
  void 'date matcher does not fail if the the example matches the pattern'() {
    matchers.date('yyyyMMdd', '20011010')
  }

  @Test(expected = InvalidMatcherException)
  void 'time matcher fails if the the example does not match the given pattern'() {
    matchers.date('HHmmss', '147812')
  }

  @Test
  void 'time matcher does not fail if the the example matches the pattern'() {
    matchers.date('HH:mm:ss.SSS', '14:34:32.678')
  }

  @Test(expected = InvalidMatcherException)
  void 'uuid matcher fails if the the example is not an UUID'() {
    matchers.uuid('aksdfkdshfkjdhf23876r872')
  }

  @Test
  void 'uuid matcher does not fail if the the example is an UUID'() {
    matchers.uuid('74a7c275-ee8b-4019-b4eb-3e37f7cde95f')
  }

  @Test(expected = InvalidMatcherException)
  void 'min like matcher fails if the the number of examples is less than the min'() {
    matchers.minLike(3, 2, 100)
  }

  @Test
  void 'min like matcher does not fail if the the number of examples is the default'() {
    matchers.minLike(3, 100)
  }

  @Test(expected = InvalidMatcherException)
  void 'max like matcher fails if the the number of examples is more than the max'() {
    matchers.maxLike(3, 4, 100)
  }

  @Test(expected = InvalidMatcherException)
  void 'minmax like matcher fails if the the number of examples is less than the min'() {
    matchers.minMaxLike(3, 4, 2, 100)
  }

  @Test
  void 'minmax like matcher does not fail if the the number of examples is the default'() {
    matchers.minMaxLike(3, 4, 100)
  }

  @Test(expected = InvalidMatcherException)
  void 'minmax like matcher fails if the the number of examples is more than the max'() {
    matchers.minMaxLike(2, 3, 4, 100)
  }

  @Test(expected = InvalidMatcherException)
  void 'minmax like matcher fails if the min is more than the max'() {
    matchers.minMaxLike(4, 3, 3, 100)
  }

}
