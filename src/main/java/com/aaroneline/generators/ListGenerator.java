package com.aaroneline.generators;

import com.aaroneline.Generator;
import com.aaroneline.TestCase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates {@code List<T>} values.
 *
 * <p>When the element generator is a {@link BasicGenerator}, a single server
 * round-trip is used (the list schema is sent directly). Otherwise, the
 * collection protocol is used to determine the list size dynamically.
 */
public final class ListGenerator<T> implements Generator<List<T>> {

    private final Generator<T> elements;
    private int minSize = 0;
    private Integer maxSize;
    private boolean unique = false;

    public ListGenerator(Generator<T> elements) {
        this.elements = elements;
    }

    /** Sets the minimum list size (inclusive, default 0). Returns {@code this} for chaining. */
    public ListGenerator<T> minSize(int min) {
        this.minSize = min;
        return this;
    }

    /** Sets the maximum list size (inclusive). Returns {@code this} for chaining. */
    public ListGenerator<T> maxSize(int max) {
        this.maxSize = max;
        return this;
    }

    /**
     * If {@code true}, all list elements will be distinct (no duplicates).
     * Returns {@code this} for chaining.
     */
    public ListGenerator<T> unique(boolean u) {
        this.unique = u;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> draw(TestCase tc) {
        tc.getDataSource().startSpan(SpanLabels.LIST);

        List<T> result;
        if (elements instanceof BasicGenerator<T> basic) {
            // Single round-trip: send the list schema, server returns the whole array.
            result = drawWithSchema(tc, basic);
        } else {
            // Collection protocol: server decides element count.
            result = drawWithCollection(tc);
        }

        tc.getDataSource().stopSpan(false);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<T> drawWithSchema(TestCase tc, BasicGenerator<T> basic) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "list");
        schema.put("elements", basic.schema());
        if (minSize > 0) schema.put("min_size", minSize);
        if (maxSize != null) schema.put("max_size", maxSize);
        if (unique) schema.put("unique", true);

        Object raw = tc.getDataSource().generate(schema);
        List<Object> rawList = (List<Object>) raw;
        List<T> out = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            out.add(basic.parseRaw(item));
        }
        return out;
    }

    private List<T> drawWithCollection(TestCase tc) {
        long collId = tc.getDataSource().newCollection(minSize, maxSize != null ? (long) maxSize : null);
        List<T> out = new ArrayList<>();
        while (tc.getDataSource().collectionMore(collId)) {
            tc.getDataSource().startSpan(SpanLabels.LIST_ELEMENT);
            T item = elements.draw(tc);
            tc.getDataSource().stopSpan(false);
            out.add(item);
        }
        return out;
    }
}
