package io.hegel.generators;

import io.hegel.Generator;
import io.hegel.TestCase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates {@code Map<K,V>} values.
 *
 * <p>When both key and value generators are {@link BasicGenerator}s, a single
 * server round-trip is used (the dict schema). The server returns pairs as
 * {@code [[k1,v1],[k2,v2],...]}.
 */
public final class MapGenerator<K, V> implements Generator<Map<K, V>> {

    private final Generator<K> keys;
    private final Generator<V> values;
    private int minSize = 0;
    private Integer maxSize;

    public MapGenerator(Generator<K> keys, Generator<V> values) {
        this.keys = keys;
        this.values = values;
    }

    /** Sets the minimum number of map entries (inclusive, default 0). Returns {@code this} for chaining. */
    public MapGenerator<K, V> minSize(int min) {
        this.minSize = min;
        return this;
    }

    /** Sets the maximum number of map entries (inclusive). Returns {@code this} for chaining. */
    public MapGenerator<K, V> maxSize(int max) {
        this.maxSize = max;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<K, V> draw(TestCase tc) {
        tc.getDataSource().startSpan(SpanLabels.MAP);

        Map<K, V> result;
        if (keys instanceof BasicGenerator<K> bk && values instanceof BasicGenerator<V> bv) {
            result = drawWithSchema(tc, bk, bv);
        } else {
            result = drawWithCollection(tc);
        }

        tc.getDataSource().stopSpan(false);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<K, V> drawWithSchema(TestCase tc, BasicGenerator<K> bk, BasicGenerator<V> bv) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "dict");
        schema.put("keys", bk.schema());
        schema.put("values", bv.schema());
        if (minSize > 0) schema.put("min_size", minSize);
        if (maxSize != null) schema.put("max_size", maxSize);

        Object raw = tc.getDataSource().generate(schema);
        // Server returns [[k1,v1],[k2,v2],...]
        List<List<Object>> pairs = (List<List<Object>>) raw;
        Map<K, V> out = new LinkedHashMap<>(pairs.size() * 2);
        for (List<Object> pair : pairs) {
            K k = bk.parseRaw(pair.get(0));
            V v = bv.parseRaw(pair.get(1));
            out.put(k, v);
        }
        return out;
    }

    private Map<K, V> drawWithCollection(TestCase tc) {
        long collId = tc.getDataSource().newCollection(minSize, maxSize != null ? (long) maxSize : null);
        Map<K, V> out = new LinkedHashMap<>();
        while (tc.getDataSource().collectionMore(collId)) {
            tc.getDataSource().startSpan(SpanLabels.MAP_ENTRY);
            K k = keys.draw(tc);
            V v = values.draw(tc);
            tc.getDataSource().stopSpan(false);
            out.put(k, v);
        }
        return out;
    }
}
