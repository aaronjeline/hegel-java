package io.hegel.generators;

import io.hegel.Generator;
import io.hegel.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates fixed-length heterogeneous lists (tuples).
 *
 * <p>Each element is drawn individually. The Hegel server accepts tuple schemas,
 * but Python's CBOR decoder currently turns nested arrays of maps into immutable
 * structures that cannot be used as the server's JSON cache key.
 */
public final class TupleGenerator implements Generator<List<Object>> {

    private final List<Generator<?>> elementGenerators;

    public TupleGenerator(List<Generator<?>> elementGenerators) {
        this.elementGenerators = List.copyOf(elementGenerators);
    }

    public TupleGenerator(Generator<?> a, Generator<?> b) {
        this.elementGenerators = List.of(a, b);
    }

    @Override
    public List<Object> draw(TestCase tc) {
        tc.getDataSource().startSpan(SpanLabels.TUPLE);
        List<Object> result = drawIndividually(tc);
        tc.getDataSource().stopSpan(false);
        return result;
    }

    private List<Object> drawIndividually(TestCase tc) {
        List<Object> out = new ArrayList<>(elementGenerators.size());
        for (Generator<?> gen : elementGenerators) {
            out.add(gen.draw(tc));
        }
        return out;
    }
}
