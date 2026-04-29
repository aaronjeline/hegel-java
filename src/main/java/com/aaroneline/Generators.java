package com.aaroneline;

import com.aaroneline.generators.*;
import com.aaroneline.generators.domain.DomainGenerators.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Static factory methods for all built-in generators.
 *
 * <p>All methods return builder objects whose configuration methods return
 * {@code this} for chaining.
 *
 * <p>Example:
 * <pre>{@code
 * int n = tc.draw(Generators.integers(1, 100));
 * List<String> words = tc.draw(Generators.lists(Generators.strings().maxSize(20)));
 * }</pre>
 */
public final class Generators {

    private Generators() {}

    // ── Primitives ─────────────────────────────────────────────────────────────

    /** Generates arbitrary {@code int} values. */
    public static IntegerGenerator integers() {
        return new IntegerGenerator();
    }

    /** Generates {@code int} values in {@code [min, max]}. */
    public static IntegerGenerator integers(int min, int max) {
        return new IntegerGenerator().minValue(min).maxValue(max);
    }

    /** Generates arbitrary {@code long} values. */
    public static LongGenerator longs() {
        return new LongGenerator();
    }

    /** Generates {@code long} values in {@code [min, max]}. */
    public static LongGenerator longs(long min, long max) {
        return new LongGenerator().minValue(min).maxValue(max);
    }

    /** Generates arbitrary {@code double} values (including NaN and Infinity). */
    public static DoubleGenerator doubles() {
        return new DoubleGenerator();
    }

    /** Generates {@code double} values in {@code [min, max]} (no NaN, no Infinity). */
    public static DoubleGenerator doubles(double min, double max) {
        return new DoubleGenerator().minValue(min).maxValue(max).allowNan(false).allowInfinity(false);
    }

    /** Generates {@code boolean} values. */
    public static BooleanGenerator booleans() {
        return new BooleanGenerator();
    }

    // ── Strings and bytes ──────────────────────────────────────────────────────

    /** Generates arbitrary Unicode strings. */
    public static StringGenerator strings() {
        return new StringGenerator();
    }

    /** Generates arbitrary byte arrays. */
    public static BytesGenerator bytes() {
        return new BytesGenerator();
    }

    // ── Collections ────────────────────────────────────────────────────────────

    /** Generates {@code List<T>} from the given element generator. */
    public static <T> ListGenerator<T> lists(Generator<T> elements) {
        return new ListGenerator<>(elements);
    }

    /** Generates {@code Set<T>} with distinct elements. */
    public static <T> SetGenerator<T> sets(Generator<T> elements) {
        return new SetGenerator<>(elements);
    }

    /** Generates {@code Map<K,V>}. */
    public static <K, V> MapGenerator<K, V> maps(Generator<K> keys, Generator<V> values) {
        return new MapGenerator<>(keys, values);
    }

    /**
     * Generates a fixed-length heterogeneous list (tuple).
     * Returned as {@code List<Object>}; callers must cast elements.
     */
    public static TupleGenerator tuples(Generator<?>... elements) {
        return new TupleGenerator(Arrays.asList(elements));
    }

    // ── Combinators ────────────────────────────────────────────────────────────

    /** Generates a value from one of the given generators. */
    @SafeVarargs
    public static <T> OneOfGenerator<T> oneOf(Generator<T>... generators) {
        return new OneOfGenerator<>(Arrays.asList(generators));
    }

    /** Generates a value from one of the given generators. */
    public static <T> OneOfGenerator<T> oneOf(List<Generator<T>> generators) {
        return new OneOfGenerator<>(generators);
    }

    /** Uniformly samples from a fixed list of values. */
    @SafeVarargs
    public static <T> SampledFromGenerator<T> sampledFrom(T... values) {
        return new SampledFromGenerator<>(Arrays.asList(values));
    }

    /** Uniformly samples from a fixed list of values. */
    public static <T> SampledFromGenerator<T> sampledFrom(List<T> values) {
        return new SampledFromGenerator<>(values);
    }

    /** Always generates the given constant value. */
    public static <T> ConstantGenerator<T> constant(T value) {
        return new ConstantGenerator<>(value);
    }

    /** Generates {@code Optional.empty()} or {@code Optional.of(value)}. */
    public static <T> OptionalGenerator<T> optional(Generator<T> inner) {
        return new OptionalGenerator<>(inner);
    }

    // ── Domain-specific ────────────────────────────────────────────────────────

    /** Generates RFC 5322 email address strings. */
    public static EmailGenerator emails() {
        return new EmailGenerator();
    }

    /** Generates HTTP/HTTPS URL strings. */
    public static UrlGenerator urls() {
        return new UrlGenerator();
    }

    /** Generates fully qualified domain name strings. */
    public static DomainGenerator domains() {
        return new DomainGenerator(null);
    }

    /** Generates IPv4 address strings. */
    public static IpAddressGenerator ipv4Addresses() {
        return new IpAddressGenerator("ipv4");
    }

    /** Generates IPv6 address strings. */
    public static IpAddressGenerator ipv6Addresses() {
        return new IpAddressGenerator("ipv6");
    }

    // ── Date / Time ────────────────────────────────────────────────────────────

    /** Generates ISO 8601 date strings, e.g. {@code "2024-03-15"}. */
    public static DateGenerator dates() {
        return new DateGenerator();
    }

    /** Generates ISO 8601 time strings, e.g. {@code "14:30:00"}. */
    public static TimeGenerator times() {
        return new TimeGenerator();
    }

    /** Generates ISO 8601 datetime strings, e.g. {@code "2024-03-15T14:30:00"}. */
    public static DateTimeGenerator dateTimes() {
        return new DateTimeGenerator();
    }
}
