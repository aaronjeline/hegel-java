package io.hegel.generators;

import io.hegel.Generator;
import io.hegel.TestCase;

import java.util.Map;
import java.util.function.Function;

/**
 * Base class for generators that communicate a single CBOR schema to the server
 * and parse the response, using a single round-trip per drawn value.
 *
 * <p>Subclasses provide:
 * <ul>
 *   <li>{@link #schema()} — the CBOR map describing what to generate
 *   <li>{@link #parseRaw(Object)} — converts the raw CBOR result to {@code T}
 * </ul>
 *
 * <p>Calling {@link #map(Function)} on a {@code BasicGenerator} returns a new
 * {@code BasicGenerator} that reuses the same schema, preserving the single
 * round-trip optimization.
 */
public abstract class BasicGenerator<T> implements Generator<T> {

    /** Returns the CBOR schema map to send in a {@code generate} command. */
    public abstract Map<String, Object> schema();

    /** Converts a raw CBOR value (deserialized by Jackson) into {@code T}. */
    public abstract T parseRaw(Object raw);

    @Override
    public T draw(TestCase tc) {
        Object raw = tc.getDataSource().generate(schema());
        return parseRaw(raw);
    }

    /**
     * Overridden to return a {@code BasicGenerator} that preserves the schema.
     * This allows chained {@code map} calls to avoid extra server round-trips.
     */
    @Override
    public <U> BasicGenerator<U> map(Function<T, U> f) {
        BasicGenerator<T> self = this;
        return new BasicGenerator<U>() {
            @Override
            public Map<String, Object> schema() {
                return self.schema();
            }

            @Override
            public U parseRaw(Object raw) {
                return f.apply(self.parseRaw(raw));
            }
        };
    }
}
