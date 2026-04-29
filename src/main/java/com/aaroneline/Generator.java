package com.aaroneline;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Produces values of type {@code T} during a Hegel test case.
 *
 * <p>Obtain generator instances via the static factory methods on
 * {@link Generators}.
 *
 * <p>Generators are composable: use {@link #map}, {@link #filter}, and
 * {@link #flatMap} to build derived generators.
 *
 * @param <T> the type of value this generator produces
 */
@FunctionalInterface
public interface Generator<T> {

    /**
     * Produce a value for the given test case.
     *
     * <p>This method should not normally be called directly; use
     * {@link TestCase#draw(Generator)} instead.
     */
    T draw(TestCase tc);

    /**
     * Return a new generator that applies {@code f} to every generated value.
     *
     * <p>When this generator is a {@link com.aaroneline.generators.BasicGenerator},
     * the schema is preserved so that generation uses a single server round-trip.
     */
    default <U> Generator<U> map(Function<T, U> f) {
        Generator<T> self = this;
        return tc -> f.apply(self.draw(tc));
    }

    /**
     * Return a new generator that only keeps values satisfying {@code predicate}.
     *
     * <p>Retries up to 3 times; if no value passes, rejects the test case via
     * {@code tc.assume(false)}.
     */
    default Generator<T> filter(Predicate<T> predicate) {
        Generator<T> self = this;
        return tc -> {
            for (int i = 0; i < 3; i++) {
                tc.getDataSource().startSpan(com.aaroneline.generators.SpanLabels.FILTER);
                T value = self.draw(tc);
                if (predicate.test(value)) {
                    tc.getDataSource().stopSpan(false);
                    return value;
                }
                tc.getDataSource().stopSpan(true);
            }
            tc.assume(false);
            throw new AssertionError("unreachable");
        };
    }

    /**
     * Return a new generator that uses the drawn value to select the next
     * generator.
     */
    default <U> Generator<U> flatMap(Function<T, Generator<U>> f) {
        Generator<T> self = this;
        return tc -> {
            tc.getDataSource().startSpan(com.aaroneline.generators.SpanLabels.FLAT_MAP);
            T intermediate = self.draw(tc);
            U result = f.apply(intermediate).draw(tc);
            tc.getDataSource().stopSpan(false);
            return result;
        };
    }
}
