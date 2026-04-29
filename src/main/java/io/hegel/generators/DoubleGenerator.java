package io.hegel.generators;

import java.util.LinkedHashMap;
import java.util.Map;

/** Generates {@code double} values. */
public final class DoubleGenerator extends BasicGenerator<Double> {

    private Double minValue;
    private Double maxValue;
    private Boolean allowNan;
    private Boolean allowInfinity;

    public DoubleGenerator() {}

    /** Sets the minimum value (inclusive). Returns {@code this} for chaining. */
    public DoubleGenerator minValue(double min) {
        this.minValue = min;
        return this;
    }

    /** Sets the maximum value (inclusive). Returns {@code this} for chaining. */
    public DoubleGenerator maxValue(double max) {
        this.maxValue = max;
        return this;
    }

    /**
     * Whether to allow {@code NaN} values (default: {@code true}).
     * Returns {@code this} for chaining.
     */
    public DoubleGenerator allowNan(boolean allow) {
        this.allowNan = allow;
        return this;
    }

    /**
     * Whether to allow {@code +/-Infinity} values (default: {@code true}).
     * Returns {@code this} for chaining.
     */
    public DoubleGenerator allowInfinity(boolean allow) {
        this.allowInfinity = allow;
        return this;
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "float");
        m.put("width", 64);
        if (minValue != null) m.put("min_value", minValue);
        if (maxValue != null) m.put("max_value", maxValue);
        if (allowNan != null) m.put("allow_nan", allowNan);
        if (allowInfinity != null) m.put("allow_infinity", allowInfinity);
        return m;
    }

    @Override
    public Double parseRaw(Object raw) {
        return ((Number) raw).doubleValue();
    }
}
