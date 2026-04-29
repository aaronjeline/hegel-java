package com.aaroneline.hegeljava.backend;

import com.aaroneline.hegeljava.protocol.Connection;
import com.aaroneline.hegeljava.protocol.HegelStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServerDataSource} error mapping and null-handling.
 *
 * <p>Uses piped connections so no external server is needed.
 */
class ServerDataSourceTest {

    private static Connection[] connectedPair() throws IOException {
        PipedOutputStream aOut = new PipedOutputStream();
        PipedInputStream  bIn  = new PipedInputStream(aOut, 65536);
        PipedOutputStream bOut = new PipedOutputStream();
        PipedInputStream  aIn  = new PipedInputStream(bOut, 65536);
        return new Connection[]{ new Connection(aIn, aOut), new Connection(bIn, bOut) };
    }

    /** Starts a daemon thread that receives one request on {@code serverStream} and replies. */
    private static void mockRespond(HegelStream serverStream, Map<String, Object> response) {
        Thread t = new Thread(() -> {
            try {
                Map.Entry<Integer, byte[]> req = serverStream.receiveRequest();
                serverStream.writeReply(req.getKey(),
                        HegelStream.CBOR.writeValueAsBytes(response));
            } catch (Exception ignored) {}
        }, "mock-server");
        t.setDaemon(true);
        t.start();
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    @Test
    void generate_unsatisfiedAssumption_throwsAssumeFailedException() throws IOException {
        Connection[] pair = connectedPair();
        HegelStream clientStream = pair[0].newStream();
        ServerDataSource ds = new ServerDataSource(clientStream);

        LinkedHashMap<String, Object> err = new LinkedHashMap<>();
        err.put("error", "assumption not satisfied");
        err.put("type", "UnsatisfiedAssumption");
        mockRespond(pair[1].connectStream(clientStream.streamId), err);

        assertThrows(AssumeFailedException.class,
                () -> ds.generate(Map.of("type", "integer")));
    }

    @Test
    void generate_stopTest_throwsStopTestException() throws IOException {
        Connection[] pair = connectedPair();
        HegelStream clientStream = pair[0].newStream();
        ServerDataSource ds = new ServerDataSource(clientStream);

        LinkedHashMap<String, Object> err = new LinkedHashMap<>();
        err.put("error", "test stopped");
        err.put("type", "StopTest");
        mockRespond(pair[1].connectStream(clientStream.streamId), err);

        assertThrows(StopTestException.class,
                () -> ds.generate(Map.of("type", "integer")));
    }

    @Test
    void generate_overflow_throwsStopTestException() throws IOException {
        Connection[] pair = connectedPair();
        HegelStream clientStream = pair[0].newStream();
        ServerDataSource ds = new ServerDataSource(clientStream);

        LinkedHashMap<String, Object> err = new LinkedHashMap<>();
        err.put("error", "overflow");
        err.put("type", "overflow");
        mockRespond(pair[1].connectStream(clientStream.streamId), err);

        assertThrows(StopTestException.class,
                () -> ds.generate(Map.of("type", "integer")));
    }

    @Test
    void generate_stopTest_setsAbortedFlag() throws IOException {
        Connection[] pair = connectedPair();
        HegelStream clientStream = pair[0].newStream();
        ServerDataSource ds = new ServerDataSource(clientStream);

        LinkedHashMap<String, Object> err = new LinkedHashMap<>();
        err.put("error", "stop");
        err.put("type", "StopTest");
        mockRespond(pair[1].connectStream(clientStream.streamId), err);

        assertThrows(StopTestException.class,
                () -> ds.generate(Map.of("type", "boolean")));
        assertTrue(ds.testAborted());
    }

    @Test
    void generate_afterAbort_throwsImmediatelyWithoutNetworkCall() throws IOException {
        Connection[] pair = connectedPair();
        HegelStream clientStream = pair[0].newStream();
        ServerDataSource ds = new ServerDataSource(clientStream);

        // Trigger abort via StopTest
        LinkedHashMap<String, Object> err = new LinkedHashMap<>();
        err.put("error", "stop");
        err.put("type", "StopTest");
        mockRespond(pair[1].connectStream(clientStream.streamId), err);

        assertThrows(StopTestException.class, () -> ds.generate(Map.of("type", "integer")));

        // No mock server set up — if this tried a network call it would block forever.
        // It must return immediately because aborted=true.
        assertThrows(StopTestException.class, () -> ds.generate(Map.of("type", "integer")));
    }

    @Test
    void generate_success_returnsValue() throws IOException {
        Connection[] pair = connectedPair();
        HegelStream clientStream = pair[0].newStream();
        ServerDataSource ds = new ServerDataSource(clientStream);

        LinkedHashMap<String, Object> ok = new LinkedHashMap<>();
        ok.put("result", 99);
        mockRespond(pair[1].connectStream(clientStream.streamId), ok);

        Object value = ds.generate(Map.of("type", "integer"));
        assertEquals(99, value);
    }

    // ── Null-origin handling ──────────────────────────────────────────────────

    @Test
    void markComplete_valid_withNullOrigin_doesNotThrow() throws IOException {
        Connection[] pair = connectedPair();
        HegelStream clientStream = pair[0].newStream();
        ServerDataSource ds = new ServerDataSource(clientStream);

        LinkedHashMap<String, Object> ok = new LinkedHashMap<>();
        ok.put("result", true);
        mockRespond(pair[1].connectStream(clientStream.streamId), ok);

        // Null origin is the normal case for VALID/INVALID completions.
        assertDoesNotThrow(() -> ds.markComplete("VALID", null));
    }

    @Test
    void markComplete_interesting_withNullOrigin_doesNotThrow() throws IOException {
        Connection[] pair = connectedPair();
        HegelStream clientStream = pair[0].newStream();
        ServerDataSource ds = new ServerDataSource(clientStream);

        LinkedHashMap<String, Object> ok = new LinkedHashMap<>();
        ok.put("result", true);
        mockRespond(pair[1].connectStream(clientStream.streamId), ok);

        // INTERESTING with null origin occurs when the thrown exception has no message.
        assertDoesNotThrow(() -> ds.markComplete("INTERESTING", null));
    }

    @Test
    void markComplete_nullOriginSerializesAsCborNull() throws Exception {
        // Verify the CBOR payload sent for mark_complete encodes null origin correctly.
        // Uses the same LinkedHashMap path as ServerDataSource.command().
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("command", "mark_complete");
        payload.put("status", "VALID");
        payload.put("origin", null);

        byte[] bytes = HegelStream.CBOR.writeValueAsBytes(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = HegelStream.CBOR.readValue(bytes, Map.class);
        assertEquals("mark_complete", decoded.get("command"));
        assertEquals("VALID", decoded.get("status"));
        assertTrue(decoded.containsKey("origin"));
        assertNull(decoded.get("origin"));
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void testAborted_initiallyFalse() throws IOException {
        Connection[] pair = connectedPair();
        ServerDataSource ds = new ServerDataSource(pair[0].newStream());
        assertFalse(ds.testAborted());
    }
}
