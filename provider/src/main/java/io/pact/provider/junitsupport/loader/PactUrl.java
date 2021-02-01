package io.pact.provider.junitsupport.loader;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to point Pact runner to source of pacts for contract tests
 *
 * @see PactUrlLoader pact loader
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@PactSource(PactUrlLoader.class)
@Inherited
public @interface PactUrl {
  /**
   * @return a list of urls to pact files
   */
  String[] urls();

  /**
   * Authentication to use, if needed. For basic auth, set the username and password. For bearer tokens, use the
   * token attribute.
   */
  Authentication auth() default @Authentication();
}
