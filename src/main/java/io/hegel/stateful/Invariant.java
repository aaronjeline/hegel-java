package io.hegel.stateful;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a state machine invariant.
 *
 * <p>Invariant methods are called after each rule execution and after
 * initialization. They must have the signature {@code void name()} and
 * should throw an assertion error if the invariant is violated.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Invariant {}
