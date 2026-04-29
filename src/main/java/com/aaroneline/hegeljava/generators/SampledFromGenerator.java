package com.aaroneline.hegeljava.generators;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a value uniformly sampled from a fixed list.
 *
 * <p>Uses an integer index schema so the server can shrink toward smaller
 * indices. Always a {@link BasicGenerator} as long as the list is non-empty.
 */
public final class SampledFromGenerator<T> extends BasicGenerator<T> {

    private final List<T> elements;

    public SampledFromGenerator(List<T> elements) {
        if (elements.isEmpty()) throw new IllegalArgumentException("sampledFrom list must not be empty");
        this.elements = List.copyOf(elements);
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "integer");
        m.put("min_value", 0);
        m.put("max_value", elements.size() - 1);
        return m;
    }

    @Override
    public T parseRaw(Object raw) {
        int idx = ((Number) raw).intValue();
        return elements.get(idx);
    }
}
