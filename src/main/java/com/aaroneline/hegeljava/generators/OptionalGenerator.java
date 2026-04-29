package com.aaroneline.hegeljava.generators;

import com.aaroneline.hegeljava.Generator;
import com.aaroneline.hegeljava.TestCase;

import java.util.Map;
import java.util.Optional;

/**
 * Generates {@code Optional<T>}: either {@code Optional.empty()} or
 * {@code Optional.of(value)} from the inner generator.
 */
public final class OptionalGenerator<T> implements Generator<Optional<T>> {

    private final Generator<T> inner;

    public OptionalGenerator(Generator<T> inner) {
        this.inner = inner;
    }

    @Override
    public Optional<T> draw(TestCase tc) {
        tc.getDataSource().startSpan(SpanLabels.OPTIONAL);
        boolean present = (Boolean) tc.getDataSource().generate(Map.of("type", "boolean"));
        Optional<T> result = present ? Optional.of(inner.draw(tc)) : Optional.empty();
        tc.getDataSource().stopSpan(false);
        return result;
    }
}
