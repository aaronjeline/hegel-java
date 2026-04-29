package com.aaroneline.generators;

import com.aaroneline.Generator;
import com.aaroneline.TestCase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a value from one of several generators, chosen by the server.
 *
 * <p>The server chooses an integer index and the selected generator is drawn
 * individually. This avoids nested arrays of schema maps, which currently do
 * not round-trip through the server's Python CBOR decoder as JSON-cacheable
 * values.
 */
public final class OneOfGenerator<T> implements Generator<T> {

    private final List<Generator<T>> generators;

    public OneOfGenerator(List<Generator<T>> generators) {
        if (generators.isEmpty()) throw new IllegalArgumentException("oneOf requires at least one generator");
        this.generators = List.copyOf(generators);
    }

    @Override
    public T draw(TestCase tc) {
        tc.getDataSource().startSpan(SpanLabels.ONE_OF);
        T result = drawByIndex(tc);
        tc.getDataSource().stopSpan(false);
        return result;
    }

    private T drawByIndex(TestCase tc) {
        Map<String, Object> indexSchema = new LinkedHashMap<>();
        indexSchema.put("type", "integer");
        indexSchema.put("min_value", 0);
        indexSchema.put("max_value", generators.size() - 1);
        int idx = ((Number) tc.getDataSource().generate(indexSchema)).intValue();
        return generators.get(idx).draw(tc);
    }
}
