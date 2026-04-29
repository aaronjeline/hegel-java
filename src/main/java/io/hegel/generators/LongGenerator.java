package io.hegel.generators;

import java.util.LinkedHashMap;
import java.util.Map;

/** Generates {@code long} values, optionally bounded. */
public final class LongGenerator extends BasicGenerator<Long> {

    private Long minValue;
    private Long maxValue;

    public LongGenerator() {}

    /** Sets the minimum value (inclusive). Returns {@code this} for chaining. */
    public LongGenerator minValue(long min) {
        this.minValue = min;
        return this;
    }

    /** Sets the maximum value (inclusive). Returns {@code this} for chaining. */
    public LongGenerator maxValue(long max) {
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
    public Long parseRaw(Object raw) {
        return ((Number) raw).longValue();
    }
}
