package com.aaroneline.hegeljava.generators;

import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates {@link String} values.
 *
 * <p>The protocol returns strings as CBOR tag 91 wrapping a byte array whose
 * content is the UTF-8 encoding of the string (surrogates allowed). Jackson
 * CBOR ignores unknown tags and returns the byte array, which is decoded here
 * with surrogate-tolerant UTF-8 decoding.
 */
public final class StringGenerator extends BasicGenerator<String> {

    private Integer minSize;
    private Integer maxSize;

    public StringGenerator() {}

    /** Sets the minimum string length in Unicode code points (inclusive). Returns {@code this} for chaining. */
    public StringGenerator minSize(int min) {
        this.minSize = min;
        return this;
    }

    /** Sets the maximum string length in Unicode code points (inclusive). Returns {@code this} for chaining. */
    public StringGenerator maxSize(int max) {
        this.maxSize = max;
        return this;
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        if (minSize != null) m.put("min_size", minSize);
        if (maxSize != null) m.put("max_size", maxSize);
        return m;
    }

    @Override
    public String parseRaw(Object raw) {
        if (raw instanceof String s) return s;
        if (raw instanceof byte[] bytes) return decodeSurrogatePermitting(bytes);
        throw new IllegalArgumentException("Unexpected type for string: " +
                (raw == null ? "null" : raw.getClass().getName()));
    }

    private static String decodeSurrogatePermitting(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            // REPLACE policy makes this unreachable, but just in case:
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
