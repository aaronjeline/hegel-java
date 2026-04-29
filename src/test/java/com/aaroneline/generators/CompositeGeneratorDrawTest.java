package com.aaroneline.generators;

import com.aaroneline.Generator;
import com.aaroneline.TestCase;
import com.aaroneline.backend.DataSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class CompositeGeneratorDrawTest {

    @Test
    void tupleDrawsElementsIndividually() {
        RecordingDataSource ds = new RecordingDataSource("key", 7);
        TestCase tc = new TestCase(ds, false, ignored -> {});

        List<Object> value = new TupleGenerator(List.of(
                new StringGenerator(),
                new IntegerGenerator()))
                .draw(tc);

        assertEquals(List.of("key", 7), value);
        assertEquals(List.of("string", "integer"), ds.generatedTypes());
        assertFalse(ds.generatedTypes().contains("tuple"));
    }

    @Test
    void oneOfDrawsIndexThenSelectedGenerator() {
        RecordingDataSource ds = new RecordingDataSource(1, "chosen");
        TestCase tc = new TestCase(ds, false, ignored -> {});

        Generator<String> generator = new OneOfGenerator<>(List.of(
                new ConstantGenerator<>("unused"),
                new StringGenerator()));

        assertEquals("chosen", generator.draw(tc));
        assertEquals(List.of("integer", "string"), ds.generatedTypes());
        assertFalse(ds.generatedTypes().contains("one_of"));
        assertFalse(ds.generatedTypes().contains("tuple"));
    }

    @Test
    void optionalDrawsPresenceBooleanThenValue() {
        RecordingDataSource ds = new RecordingDataSource(true, 42);
        TestCase tc = new TestCase(ds, false, ignored -> {});

        Optional<Integer> value = new OptionalGenerator<>(new IntegerGenerator()).draw(tc);

        assertEquals(Optional.of(42), value);
        assertEquals(List.of("boolean", "integer"), ds.generatedTypes());
        assertFalse(ds.generatedTypes().contains("one_of"));
        assertFalse(ds.generatedTypes().contains("tuple"));
    }

    private static final class RecordingDataSource implements DataSource {
        private final Queue<Object> values = new ArrayDeque<>();
        private final List<Map<String, Object>> schemas = new ArrayList<>();

        RecordingDataSource(Object... values) {
            this.values.addAll(List.of(values));
        }

        @Override
        public Object generate(Map<String, Object> schema) {
            schemas.add(schema);
            if (values.isEmpty()) {
                fail("No recorded value for schema: " + schema);
            }
            return values.remove();
        }

        List<String> generatedTypes() {
            return schemas.stream()
                    .map(schema -> (String) schema.get("type"))
                    .toList();
        }

        @Override
        public void startSpan(long label) {}

        @Override
        public void stopSpan(boolean discard) {}

        @Override
        public long newCollection(long minSize, Long maxSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean collectionMore(long collectionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void collectionReject(long collectionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long newPool() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long poolAdd(long poolId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long poolGenerate(long poolId, boolean consume) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markComplete(String status, String origin) {}

        @Override
        public boolean testAborted() {
            return false;
        }
    }
}
