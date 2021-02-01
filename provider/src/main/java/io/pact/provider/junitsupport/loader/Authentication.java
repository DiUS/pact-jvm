package io.pact.provider.junitsupport.loader;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the authentication scheme to use with URLs
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface Authentication {
  /**
   * Username to use for basic authentication
   */
  String username() default "";

  /**
   * Password to use for basic authentication
   */
  String password() default "";

  /**
   * Token to use for bearer token authentication
   */
  String token() default "";
}
