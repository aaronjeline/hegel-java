package com.aaroneline.hegeljava.generators;

import java.util.LinkedHashMap;
import java.util.Map;

/** Generates {@code byte[]} values. */
public final class BytesGenerator extends BasicGenerator<byte[]> {

    private Integer minSize;
    private Integer maxSize;

    public BytesGenerator() {}

    /** Sets the minimum byte-array length (inclusive). Returns {@code this} for chaining. */
    public BytesGenerator minSize(int min) {
        this.minSize = min;
        return this;
    }

    /** Sets the maximum byte-array length (inclusive). Returns {@code this} for chaining. */
    public BytesGenerator maxSize(int max) {
        this.maxSize = max;
        return this;
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "binary");
        if (minSize != null) m.put("min_size", minSize);
        if (maxSize != null) m.put("max_size", maxSize);
        return m;
    }

    @Override
    public byte[] parseRaw(Object raw) {
        return (byte[]) raw;
    }
}
