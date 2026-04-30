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
        BlockingQueue<Events.InvocationEvent> queue = new LinkedBlockingQueue<>();
        AtomicReference<Throwable> driverError = new AtomicReference<>();

        Thread driver = new Thread(() ->
                runDriver(session, testStream, settings, databaseKey, queue, driverError),
                "hegel-driver");
        driver.setDaemon(true);
        driver.start();

        return StreamSupport.stream(new HegelInvocationStream(queue), false);
    }

    // ── Driver thread ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void runDriver(
            HegelSession session,
            HegelStream testStream,
            Settings settings,
            byte[] databaseKey,
            BlockingQueue<Events.InvocationEvent> queue,
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
                    queue.put(new Events.TestCaseEvent(tc));

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
                queue.put(new Events.TestCaseEvent(tc));
            }

            queue.put(new Events.DoneEvent());

        } catch (Throwable t) {
            try {
                queue.put(new Events.ErrorEvent(t));
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

}
