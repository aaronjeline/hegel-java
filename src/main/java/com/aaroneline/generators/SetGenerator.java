package com.aaroneline.generators;

import com.aaroneline.Generator;
import com.aaroneline.TestCase;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates {@code Set<T>} values with distinct elements.
 *
 * <p>Backed by a {@link ListGenerator} with {@code unique = true}.
 */
public final class SetGenerator<T> implements Generator<Set<T>> {

    private final ListGenerator<T> inner;

    public SetGenerator(Generator<T> elements) {
        this.inner = new ListGenerator<>(elements).unique(true);
    }

    /** Sets the minimum set size (inclusive, default 0). Returns {@code this} for chaining. */
    public SetGenerator<T> minSize(int min) {
        inner.minSize(min);
        return this;
    }

    /** Sets the maximum set size (inclusive). Returns {@code this} for chaining. */
    public SetGenerator<T> maxSize(int max) {
        inner.maxSize(max);
        return this;
    }

    @Override
    public Set<T> draw(TestCase tc) {
        // Re-use ListGenerator but wrap result in a LinkedHashSet (preserves order, enforces uniqueness).
        // The server already guarantees uniqueness when unique=true; the LinkedHashSet is just
        // the correct return type.
        List<T> list = inner.draw(tc);
        return new LinkedHashSet<>(list);
    }
}
