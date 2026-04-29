package io.hegel.stateful;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a state machine rule.
 *
 * <p>Rule methods are randomly selected and executed during a stateful test.
 * Each rule method must have the signature:
 * <pre>{@code
 * @Rule
 * public void someName(TestCase tc) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Rule {}
