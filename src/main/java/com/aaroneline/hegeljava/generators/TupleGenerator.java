package com.aaroneline.hegeljava.generators;

import com.aaroneline.hegeljava.Generator;
import com.aaroneline.hegeljava.TestCase;
import com.aaroneline.hegeljava.tuples.Tuple;
import com.aaroneline.hegeljava.tuples.Tuple3;

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

    public static record TupleGen<X,Y>(Generator<X> a, Generator<Y> b) implements Generator<Tuple<X,Y>> {
        @Override
        public Tuple<X,Y> draw(TestCase tc) {
            tc.getDataSource().startSpan(SpanLabels.TUPLE);
            var a = this.a.draw(tc);
            var b = this.b.draw(tc);
            tc.getDataSource().stopSpan(false);
            return new Tuple<>(a,b);
        }

    }

    public static record Tuple3Gen<X,Y,Z>(Generator<X> a, Generator<Y> b, Generator<Z> c) implements Generator<Tuple3<X,Y,Z>> {
        @Override
        public Tuple3<X,Y, Z> draw(TestCase tc) {
            tc.getDataSource().startSpan(SpanLabels.TUPLE);
            var a = this.a.draw(tc);
            var b = this.b.draw(tc);
            var c = this.c.draw(tc);
            tc.getDataSource().stopSpan(false);
            return new Tuple3<>(a,b,c);
        }

    }


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
