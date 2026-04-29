package io.hegel.generators;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Always generates the same constant value.
 *
 * <p>The value must be CBOR-serializable (String, Number, Boolean, null,
 * List, or Map).
 */
public final class ConstantGenerator<T> extends BasicGenerator<T> {

    private final T value;

    public ConstantGenerator(T value) {
        this.value = value;
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "constant");
        m.put("value", value);
        return m;
    }

    @Override
    public T parseRaw(Object raw) {
        // The server echoes back our constant value; just return it.
        @SuppressWarnings("unchecked")
        T result = (T) raw;
        return result;
    }
}
