package com.aaroneline.hegeljava.generators;

import com.aaroneline.hegeljava.Generator;
import com.aaroneline.hegeljava.TestCase;
import com.aaroneline.hegeljava.tuples.Tuple;
import com.aaroneline.hegeljava.tuples.Tuple3;
import com.aaroneline.hegeljava.tuples.Tuple4;
import com.aaroneline.hegeljava.tuples.Tuple5;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates fixed-length heterogeneous lists (tuples).
 *
 * <p>Each element is drawn individually. The Hegel server accepts tuple schemas,
 * but Python's CBOR decoder currently turns nested arrays of maps into immutable
 * structures that cannot be used as the server's JSON cache key.
 */
public final class TupleGenerators {

    public static record TupleGenerator<A,B>(Generator<A> a, Generator<B> b) implements Generator<Tuple<A,B>> {
        @Override
        public Tuple<A,B> draw(TestCase tc) {
            tc.getDataSource().startSpan(SpanLabels.TUPLE);
            var a = this.a.draw(tc);
            var b = this.b.draw(tc);
            tc.getDataSource().stopSpan(false);
            return new Tuple<>(a,b);
        }

    }

    public static record Tuple3Generator<A,B,C>(Generator<A> a, Generator<B> b, Generator<C> c) implements Generator<Tuple3<A,B,C>> {
        @Override
        public Tuple3<A,B,C> draw(TestCase tc) {
            tc.getDataSource().startSpan(SpanLabels.TUPLE);
            var a = this.a.draw(tc);
            var b = this.b.draw(tc);
            var c = this.c.draw(tc);
            tc.getDataSource().stopSpan(false);
            return new Tuple3<>(a,b,c);
        }

    }

    public static record Tuple4Generator<A,B,C,D>(Generator<A> a, Generator<B> b, Generator<C> c, Generator<D> d) implements Generator<Tuple4<A,B,C,D>> {
        @Override
        public Tuple4<A,B,C,D> draw(TestCase tc) {
            tc.getDataSource().startSpan(SpanLabels.TUPLE);
            var a = this.a.draw(tc);
            var b = this.b.draw(tc);
            var c = this.c.draw(tc);
            var d = this.d.draw(tc);
            tc.getDataSource().stopSpan(false);
            return new Tuple4<>(a,b,c,d);
        }
    }

    public static record Tuple5Generator<A,B,C,D,E>(Generator<A> a, Generator<B> b, Generator<C> c, Generator<D> d, Generator<E> e) implements Generator<Tuple5<A,B,C,D,E>> {
        @Override
        public Tuple5<A,B,C,D,E> draw(TestCase tc) {
            tc.getDataSource().startSpan(SpanLabels.TUPLE);
            var a = this.a.draw(tc);
            var b = this.b.draw(tc);
            var c = this.c.draw(tc);
            var d = this.d.draw(tc);
            var e = this.e.draw(tc);
            tc.getDataSource().stopSpan(false);
            return new Tuple5<>(a,b,c,d,e);
        }
    }
}
