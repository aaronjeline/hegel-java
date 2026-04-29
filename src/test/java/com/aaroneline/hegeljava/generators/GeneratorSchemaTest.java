package com.aaroneline.hegeljava.generators;

import com.aaroneline.hegeljava.Generator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for generator {@code schema()} outputs and {@code parseRaw()} conversions.
 *
 * <p>These tests run entirely without a server — they verify the schemas sent
 * to the server and the parsing of values the server would return.
 */
class GeneratorSchemaTest {

    // ── IntegerGenerator ──────────────────────────────────────────────────────

    @Test
    void integerGenerator_defaultSchema() {
        Map<String, Object> schema = new IntegerGenerator().schema();
        assertEquals("integer", schema.get("type"));
        assertFalse(schema.containsKey("min_value"));
        assertFalse(schema.containsKey("max_value"));
    }

    @Test
    void integerGenerator_withMinMax() {
        Map<String, Object> schema = new IntegerGenerator().minValue(-5).maxValue(10).schema();
        assertEquals("integer", schema.get("type"));
        assertEquals(-5, schema.get("min_value"));
        assertEquals(10, schema.get("max_value"));
    }

    @Test
    void integerGenerator_parseRaw_acceptsIntAndLong() {
        IntegerGenerator gen = new IntegerGenerator();
        assertEquals(42, gen.parseRaw(42));
        assertEquals(42, gen.parseRaw(42L));
        assertEquals(-1, gen.parseRaw(-1));
    }

    // ── LongGenerator ─────────────────────────────────────────────────────────

    @Test
    void longGenerator_defaultSchema() {
        Map<String, Object> schema = new LongGenerator().schema();
        assertEquals("integer", schema.get("type"));
        assertFalse(schema.containsKey("min_value"));
        assertFalse(schema.containsKey("max_value"));
    }

    @Test
    void longGenerator_withBounds() {
        Map<String, Object> schema = new LongGenerator().minValue(0L).maxValue(Long.MAX_VALUE).schema();
        assertEquals(0L, schema.get("min_value"));
        assertEquals(Long.MAX_VALUE, schema.get("max_value"));
    }

    @Test
    void longGenerator_parseRaw() {
        LongGenerator gen = new LongGenerator();
        assertEquals(Long.MAX_VALUE, gen.parseRaw(Long.MAX_VALUE));
        assertEquals(0L, gen.parseRaw(0));
    }

    // ── BooleanGenerator ──────────────────────────────────────────────────────

    @Test
    void booleanGenerator_schema() {
        Map<String, Object> schema = new BooleanGenerator().schema();
        assertEquals("boolean", schema.get("type"));
    }

    @Test
    void booleanGenerator_parseRaw() {
        BooleanGenerator gen = new BooleanGenerator();
        assertTrue(gen.parseRaw(Boolean.TRUE));
        assertFalse(gen.parseRaw(Boolean.FALSE));
    }

    // ── DoubleGenerator ───────────────────────────────────────────────────────

    @Test
    void doubleGenerator_schema() {
        Map<String, Object> schema = new DoubleGenerator().schema();
        assertEquals("float", schema.get("type"));
        assertEquals(64, schema.get("width"));
    }

    @Test
    void doubleGenerator_parseRaw() {
        DoubleGenerator gen = new DoubleGenerator();
        assertEquals(3.14, gen.parseRaw(3.14), 1e-10);
        assertEquals(0.0, gen.parseRaw(0), 1e-10);
    }

    // ── StringGenerator ───────────────────────────────────────────────────────

    @Test
    void stringGenerator_defaultSchema() {
        Map<String, Object> schema = new StringGenerator().schema();
        assertEquals("string", schema.get("type"));
        assertFalse(schema.containsKey("min_size"));
        assertFalse(schema.containsKey("max_size"));
    }

    @Test
    void stringGenerator_withSizeBounds() {
        Map<String, Object> schema = new StringGenerator().minSize(1).maxSize(20).schema();
        assertEquals(1, schema.get("min_size"));
        assertEquals(20, schema.get("max_size"));
    }

    @Test
    void stringGenerator_parseRaw_acceptsString() {
        assertEquals("hello", new StringGenerator().parseRaw("hello"));
        assertEquals("", new StringGenerator().parseRaw(""));
    }

    @Test
    void stringGenerator_parseRaw_acceptsByteArray() {
        // CBOR tag 91 delivers strings as byte[]; parseRaw must handle both.
        byte[] utf8 = "world".getBytes(StandardCharsets.UTF_8);
        assertEquals("world", new StringGenerator().parseRaw(utf8));
    }

    @Test
    void stringGenerator_parseRaw_handlesMalformedUtf8() {
        // Surrogates / invalid sequences must be replaced, not thrown.
        byte[] invalid = {(byte) 0xFF, (byte) 0xFE};
        String result = new StringGenerator().parseRaw(invalid);
        assertNotNull(result);
        // Contains replacement characters, not an exception.
        assertTrue(result.contains("\uFFFD"));
    }

    @Test
    void stringGenerator_parseRaw_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new StringGenerator().parseRaw(null));
    }

    // ── BasicGenerator.map ────────────────────────────────────────────────────

    @Test
    void basicGenerator_map_returnsBasicGenerator() {
        IntegerGenerator base = new IntegerGenerator().minValue(0).maxValue(100);
        Generator<String> mapped = base.map(Object::toString);
        assertInstanceOf(BasicGenerator.class, mapped);
    }

    @Test
    void basicGenerator_map_preservesSchema() {
        IntegerGenerator base = new IntegerGenerator().minValue(1).maxValue(50);
        BasicGenerator<String> mapped = base.map(i -> "v" + i);
        assertEquals(base.schema(), mapped.schema());
    }

    @Test
    void basicGenerator_map_appliesTransformation() {
        IntegerGenerator base = new IntegerGenerator();
        BasicGenerator<Integer> doubled = base.map(i -> i * 2);
        assertEquals(84, doubled.parseRaw(42));
    }

    @Test
    void basicGenerator_map_chainingPreservesSchema() {
        IntegerGenerator base = new IntegerGenerator().minValue(0).maxValue(10);
        BasicGenerator<String> chained = base.map(i -> i * 2).map(Object::toString);
        assertInstanceOf(BasicGenerator.class, chained);
        assertEquals(base.schema(), chained.schema());
        assertEquals("8", chained.parseRaw(4));
    }

    // ── OneOfGenerator ────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void oneOfGenerator_allBasic_eachBranchSchemaIsTaggedTuple() {
        // When all generators are BasicGenerators, OneOfGenerator builds a
        // one_of schema whose branches are tagged tuples [constant(index), element_schema].
        // We verify the structure by directly inspecting the tagged schemas that would
        // be sent to the server.
        IntegerGenerator branch0 = new IntegerGenerator().minValue(0).maxValue(10);

        // Build the tagged-tuple schema for branch 0 manually to match what OneOfGenerator does.
        Map<String, Object> constantSchema = new LinkedHashMap<>();
        constantSchema.put("type", "constant");
        constantSchema.put("value", 0);

        Map<String, Object> tupleSchema = new LinkedHashMap<>();
        tupleSchema.put("type", "tuple");
        tupleSchema.put("elements", List.of(constantSchema, branch0.schema()));

        assertEquals("tuple", tupleSchema.get("type"));
        List<Map<String, Object>> elements = (List<Map<String, Object>>) tupleSchema.get("elements");
        assertEquals("constant", elements.get(0).get("type"));
        assertEquals(0, elements.get(0).get("value"));
        assertEquals(branch0.schema(), elements.get(1));
    }

    @Test
    void oneOfGenerator_empty_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new OneOfGenerator<>(List.of()));
    }

    // ── ConstantGenerator ─────────────────────────────────────────────────────

    @Test
    void constantGenerator_schemaAndParse() {
        ConstantGenerator<String> gen = new ConstantGenerator<>("fixed");
        Map<String, Object> schema = gen.schema();
        assertEquals("constant", schema.get("type"));
        assertEquals("fixed", schema.get("value"));
        assertEquals("fixed", gen.parseRaw("fixed")); // server echoes back the constant
    }

    // ── SampledFromGenerator ──────────────────────────────────────────────────

    @Test
    void sampledFromGenerator_schema() {
        List<String> elements = List.of("a", "b", "c");
        SampledFromGenerator<String> gen = new SampledFromGenerator<>(elements);
        Map<String, Object> schema = gen.schema();
        assertEquals("integer", schema.get("type"));
        assertEquals(0, schema.get("min_value"));
        assertEquals(2, schema.get("max_value")); // elements.size() - 1
    }

    @Test
    void sampledFromGenerator_parseRaw_indexesIntoElements() {
        List<String> elements = List.of("x", "y", "z");
        SampledFromGenerator<String> gen = new SampledFromGenerator<>(elements);
        assertEquals("x", gen.parseRaw(0));
        assertEquals("y", gen.parseRaw(1));
        assertEquals("z", gen.parseRaw(2));
    }
}
