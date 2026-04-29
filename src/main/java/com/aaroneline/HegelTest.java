package com.aaroneline;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a Hegel property-based test.
 *
 * <p>Replaces JUnit 5's {@code @Test}. The test method must accept a single
 * {@link TestCase} parameter:
 *
 * <pre>{@code
 * @HegelTest
 * void myProperty(TestCase tc) {
 *     int x = tc.draw(Generators.integers());
 *     assert x == x;
 * }
 * }</pre>
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@link #testCases()} — maximum number of test cases (default 100)
 *   <li>{@link #seed()} — fixed random seed for reproducibility
 *   <li>{@link #suppressHealthCheck()} — health check names to suppress
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(HegelExtension.class)
public @interface HegelTest {

    /** Maximum number of test cases to execute. */
    int testCases() default 100;

    /**
     * Fixed random seed. Use {@code Long.MIN_VALUE} (the default) to let the
     * server choose a random seed.
     */
    long seed() default Long.MIN_VALUE;

    /**
     * Health check names to suppress.
     * Valid values: {@code "test_cases_too_large"}, {@code "filter_too_much"},
     * {@code "too_slow"}, {@code "large_initial_test_case"}.
     */
    String[] suppressHealthCheck() default {};
}
