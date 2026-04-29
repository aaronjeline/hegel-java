package com.aaroneline.hegeljava.generators;

import java.util.Map;

/** Generates {@code boolean} values. */
public final class BooleanGenerator extends BasicGenerator<Boolean> {

    public BooleanGenerator() {}

    @Override
    public Map<String, Object> schema() {
        return Map.of("type", "boolean");
    }

    @Override
    public Boolean parseRaw(Object raw) {
        return (Boolean) raw;
    }
}
