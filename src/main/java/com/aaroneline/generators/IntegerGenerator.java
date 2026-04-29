package com.aaroneline.generators;

import java.util.LinkedHashMap;
import java.util.Map;

/** Generates {@code int} values, optionally bounded. */
public final class IntegerGenerator extends BasicGenerator<Integer> {

    private Integer minValue;
    private Integer maxValue;

    public IntegerGenerator() {}

    /** Sets the minimum value (inclusive). Returns {@code this} for chaining. */
    public IntegerGenerator minValue(int min) {
        this.minValue = min;
        return this;
    }

    /** Sets the maximum value (inclusive). Returns {@code this} for chaining. */
    public IntegerGenerator maxValue(int max) {
        this.maxValue = max;
        return this;
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "integer");
        if (minValue != null) m.put("min_value", minValue);
        if (maxValue != null) m.put("max_value", maxValue);
        return m;
    }

    @Override
    public Integer parseRaw(Object raw) {
        return ((Number) raw).intValue();
    }
}
