package io.hegel.protocol;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CBOR serialization of null values.
 *
 * <p>The Hegel protocol uses {@code {"result": null}} as an acknowledgement
 * for several server requests (test_case acks and final-replay acks).
 * Java's {@code Map.of} rejects null values with a NullPointerException;
 * these tests document that behaviour and verify the correct approach.
 */
class CborNullSerializationTest {

    @Test
    void mapOf_throwsNpeOnNullValue() {
        // Documenting the root cause of the ack bug:
        // Map.of rejects null values at construction time.
        assertThrows(NullPointerException.class, () -> Map.of("result", (Object) null));
    }

    @Test
    void linkedHashMap_acceptsNullValue() throws Exception {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("result", null);
        // Must not throw:
        byte[] bytes = HegelStream.CBOR.writeValueAsBytes(m);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void nullResult_roundTripsViaCbor() throws Exception {
        LinkedHashMap<String, Object> original = new LinkedHashMap<>();
        original.put("result", null);

        byte[] bytes = HegelStream.CBOR.writeValueAsBytes(original);

        @SuppressWarnings("unchecked")
        Map<String, Object> restored = HegelStream.CBOR.readValue(bytes, Map.class);

        assertTrue(restored.containsKey("result"), "key 'result' must be present");
        assertNull(restored.get("result"), "value must be null");
    }

    @Test
    void nullResult_withOtherFields_roundTrips() throws Exception {
        LinkedHashMap<String, Object> original = new LinkedHashMap<>();
        original.put("command", "mark_complete");
        original.put("status", "VALID");
        original.put("origin", null);

        byte[] bytes = HegelStream.CBOR.writeValueAsBytes(original);

        @SuppressWarnings("unchecked")
        Map<String, Object> restored = HegelStream.CBOR.readValue(bytes, Map.class);

        assertEquals("mark_complete", restored.get("command"));
        assertEquals("VALID", restored.get("status"));
        assertTrue(restored.containsKey("origin"));
        assertNull(restored.get("origin"));
    }

    @Test
    void ackNullEquivalent_producesValidCborBytes() throws Exception {
        // Reproduces the exact pattern used in HegelExtension.ackNull().
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("result", null);
        byte[] bytes = HegelStream.CBOR.writeValueAsBytes(m);

        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = HegelStream.CBOR.readValue(bytes, Map.class);
        assertTrue(decoded.containsKey("result"));
        assertNull(decoded.get("result"));
    }
}
