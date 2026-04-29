package com.aaroneline.hegeljava;

import com.aaroneline.hegeljava.backend.AssumeFailedException;
import com.aaroneline.hegeljava.backend.ServerDataSource;
import com.aaroneline.hegeljava.backend.StopTestException;
import com.aaroneline.hegeljava.protocol.Connection;
import com.aaroneline.hegeljava.protocol.HegelStream;
import org.junit.jupiter.api.extension.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * JUnit 5 extension that powers {@link HegelTest}.
 *
 * <p>Implements {@link TestTemplateInvocationContextProvider}: when a test
 * method annotated with {@code @HegelTest} runs, this extension:
 * <ol>
 *   <li>Connects to (or creates) the Hegel server session.
 *   <li>Sends a {@code run_test} command.
 *   <li>Streams {@link TestCase} invocations to JUnit — one per server
 *       {@code test_case} event.
 *   <li>Handles {@code test_done} and final replays.
 *   <li>Reports failures for the minimal counterexample.
 * </ol>
 */
public final class HegelExtension implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext ctx) {
        return ctx.getRequiredTestMethod().isAnnotationPresent(HegelTest.class);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext extensionContext) {

        HegelTest annotation = extensionContext.getRequiredTestMethod()
                .getAnnotation(HegelTest.class);
        Settings settings = Settings.from(annotation);
        byte[] databaseKey = buildDatabaseKey(extensionContext);

        HegelSession session = HegelSession.get();
        Connection conn = session.connection;

        // The test-run stream: server sends test_case events here.
        HegelStream testStream = conn.newStream();

        // Queue fed by the driver thread; consumed by our Spliterator.
        BlockingQueue<InvocationEvent> queue = new LinkedBlockingQueue<>();
        AtomicReference<Throwable> driverError = new AtomicReference<>();

        Thread driver = new Thread(() ->
                runDriver(session, testStream, settings, databaseKey, queue, driverError),
                "hegel-driver");
        driver.setDaemon(true);
        driver.start();

        return StreamSupport.stream(
                new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE, Spliterator.ORDERED) {
                    @Override
                    public boolean tryAdvance(
                            java.util.function.Consumer<? super TestTemplateInvocationContext> action) {
                        InvocationEvent event;
                        try {
                            event = queue.take();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                        if (event instanceof DoneEvent) return false;
                        if (event instanceof ErrorEvent ee) {
                            throw new RuntimeException("Hegel driver error", ee.cause());
                        }
                        TestCaseEvent tc = (TestCaseEvent) event;
                        action.accept(new HegelInvocationContext(tc.testCase()));
                        return true;
                    }
                },
                false /* not parallel */);
    }

    // ── Driver thread ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void runDriver(
            HegelSession session,
            HegelStream testStream,
            Settings settings,
            byte[] databaseKey,
            BlockingQueue<InvocationEvent> queue,
            AtomicReference<Throwable> driverError) {
        try {
            session.runTest(testStream.streamId, settings, databaseKey);

            // Main event loop
            int nInteresting = 0;
            while (true) {
                Map.Entry<Integer, byte[]> evt = testStream.receiveRequest();
                Map<String, Object> event = HegelStream.CBOR.readValue(evt.getValue(), Map.class);
                String eventType = (String) event.get("event");

                if ("test_case".equals(eventType)) {
                    int tcStreamId = ((Number) event.get("stream_id")).intValue();
                    HegelStream tcStream = session.connection.connectStream(tcStreamId);

                    // Ack BEFORE running the test (same as Rust — prevents deadlock)
                    testStream.writeReply(evt.getKey(),
                            ackNull());

                    ServerDataSource ds = new ServerDataSource(tcStream);
                    // is_final is always false in the main loop; the server may send
                    // is_final=true during shrinking but we must ignore it here, just
                    // as the Rust client hardcodes false.  Final replays are handled
                    // in the separate loop below after test_done.
                    TestCase tc = new TestCase(ds, false, msg -> System.err.println(msg));
                    queue.put(new TestCaseEvent(tc));

                } else if ("test_done".equals(eventType)) {
                    // Ack test_done
                    testStream.writeReply(evt.getKey(),
                            HegelStream.CBOR.writeValueAsBytes(Map.of("result", true)));

                    Map<String, Object> results = (Map<String, Object>) event.get("results");
                    checkResultErrors(results);

                    Object nIntObj = results != null ? results.get("interesting_test_cases") : null;
                    nInteresting = nIntObj != null ? ((Number) nIntObj).intValue() : 0;
                    break;
                }
            }

            // Final replay(s)
            // The server sends one replay per interesting (failing) test case found
            // during the run, including all shrinking steps.  Only the last replay
            // is the minimal counterexample, so only that one gets isFinalRun=true
            // (which triggers draw/note output).  Intermediate replays are run
            // silently to satisfy the protocol.
            for (int i = 0; i < nInteresting; i++) {
                Map.Entry<Integer, byte[]> evt = testStream.receiveRequest();
                Map<String, Object> event = HegelStream.CBOR.readValue(evt.getValue(), Map.class);

                int tcStreamId = ((Number) event.get("stream_id")).intValue();
                HegelStream tcStream = session.connection.connectStream(tcStreamId);

                testStream.writeReply(evt.getKey(), ackNull());

                boolean isLast = (i == nInteresting - 1);
                ServerDataSource ds = new ServerDataSource(tcStream);
                TestCase tc = new TestCase(ds, isLast /* isFinalRun */,
                        msg -> System.err.println(msg));
                queue.put(new TestCaseEvent(tc));
            }

            queue.put(DoneEvent.INSTANCE);

        } catch (Throwable t) {
            try {
                queue.put(new ErrorEvent(t));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** {@code {"result": null}} as CBOR bytes. {@code Map.of} rejects nulls, so use LinkedHashMap. */
    private static byte[] ackNull() {
        try {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("result", null);
            return HegelStream.CBOR.writeValueAsBytes(m);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkResultErrors(Map<String, Object> results) {
        if (results == null) return;
        String error = (String) results.get("error");
        if (error != null) throw new RuntimeException("Hegel server error: " + error);
        String healthCheck = (String) results.get("health_check_failure");
        if (healthCheck != null) throw new RuntimeException("Health check failure:\n" + healthCheck);
        String flaky = (String) results.get("flaky");
        if (flaky != null) throw new RuntimeException("Flaky test detected: " + flaky);
    }

    // ── Database key ───────────────────────────────────────────────────────────

    private static byte[] buildDatabaseKey(ExtensionContext ctx) {
        String key = ctx.getRequiredTestClass().getName() + "." +
                ctx.getRequiredTestMethod().getName();
        return key.getBytes(StandardCharsets.UTF_8);
    }

    // ── Event types ────────────────────────────────────────────────────────────

    private sealed interface InvocationEvent permits TestCaseEvent, DoneEvent, ErrorEvent {}

    private record TestCaseEvent(TestCase testCase) implements InvocationEvent {}

    private enum DoneEvent implements InvocationEvent {
        INSTANCE
    }

    private record ErrorEvent(Throwable cause) implements InvocationEvent {}

    // ── Per-invocation context ─────────────────────────────────────────────────

    private static final class HegelInvocationContext implements TestTemplateInvocationContext {
        private final TestCase testCase;

        HegelInvocationContext(TestCase testCase) {
            this.testCase = testCase;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return testCase.isFinalRun() ? "Counterexample" : "Case #" + invocationIndex;
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(
                    new HegelParameterResolver(testCase),
                    new HegelInvocationInterceptor(testCase));
        }
    }

    // ── Parameter resolver ─────────────────────────────────────────────────────

    private static final class HegelParameterResolver implements ParameterResolver {
        private final TestCase testCase;

        HegelParameterResolver(TestCase testCase) {
            this.testCase = testCase;
        }

        @Override
        public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
            return pc.getParameter().getType() == TestCase.class;
        }

        @Override
        public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
            return testCase;
        }
    }

    // ── Invocation interceptor ─────────────────────────────────────────────────

    private static final class HegelInvocationInterceptor implements InvocationInterceptor {
        private final TestCase testCase;

        HegelInvocationInterceptor(TestCase testCase) {
            this.testCase = testCase;
        }

        @Override
        public void interceptTestTemplateMethod(
                Invocation<Void> invocation,
                ReflectiveInvocationContext<Method> invocationContext,
                ExtensionContext extensionContext) throws Throwable {

            var ds = testCase.getDataSource();
            try {
                invocation.proceed();
                if (!ds.testAborted()) {
                    ds.markComplete("VALID", null);
                }
            } catch (AssumeFailedException e) {
                if (!ds.testAborted()) {
                    ds.markComplete("INVALID", null);
                }
                // Swallow: JUnit should not see this as a test failure.
            } catch (StopTestException e) {
                // Backend already aborted; do not call markComplete.
                // Swallow: not a test failure.
            } catch (Throwable t) {
                if (!ds.testAborted()) {
                    ds.markComplete("INTERESTING", failureOrigin(t));
                }
                if (testCase.isFinalRun()) {
                    java.util.List<String> lines = testCase.getCounterexampleLines();
                    if (!lines.isEmpty()) {
                        StringBuilder msg = new StringBuilder("Falsifying example:\n");
                        for (String line : lines) {
                            msg.append("  ").append(line).append('\n');
                        }
                        AssertionError wrapper = new AssertionError(msg.toString().stripTrailing(), t);
                        wrapper.setStackTrace(t.getStackTrace());
                        throw wrapper;
                    }
                    throw t; // Final replay: surface as JUnit failure.
                }
                // Non-final run: swallow so JUnit doesn't mark it as failed.
            }
        }

        private static String failureOrigin(Throwable t) {
            StackTraceElement[] stack = t.getStackTrace();
            if (stack.length > 0) {
                StackTraceElement frame = firstUserFrame(stack);
                return t.getClass().getName() + " at " +
                        frame.getClassName() + "." + frame.getMethodName() +
                        ":" + frame.getLineNumber();
            }
            return t.getClass().getName();
        }

        private static StackTraceElement firstUserFrame(StackTraceElement[] stack) {
            for (StackTraceElement frame : stack) {
                String cls = frame.getClassName();
                if (!cls.startsWith("com.aaroneline.hegeljava.")
                        && !cls.startsWith("org.junit.")
                        && !cls.startsWith("org.opentest4j.")
                        && !cls.startsWith("java.")
                        && !cls.startsWith("jdk.")) {
                    return frame;
                }
            }
            return stack[0];
        }
    }
}
