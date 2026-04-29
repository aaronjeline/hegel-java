package com.aaroneline.hegeljava.backend;

import com.aaroneline.hegeljava.protocol.HegelStream;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link DataSource} implementation backed by the Hegel server.
 *
 * <p>Each test case gets its own {@code ServerDataSource} wrapping a dedicated
 * per-test-case {@link HegelStream}. All communication happens synchronously on
 * the calling thread.
 */
public final class ServerDataSource implements DataSource {

    private final HegelStream stream;
    private final AtomicBoolean aborted = new AtomicBoolean(false);

    public ServerDataSource(HegelStream stream) {
        this.stream = stream;
    }

    // ── DataSource implementation ──────────────────────────────────────────────

    @Override
    public Object generate(Map<String, Object> schema) {
        Map<String, Object> payload = command("generate");
        payload.put("schema", schema);
        return sendCommand(payload);
    }

    @Override
    public void startSpan(long label) {
        Map<String, Object> payload = command("start_span");
        payload.put("label", label);
        sendCommand(payload);
    }

    @Override
    public void stopSpan(boolean discard) {
        Map<String, Object> payload = command("stop_span");
        payload.put("discard", discard);
        sendCommand(payload);
    }

    @Override
    public long newCollection(long minSize, Long maxSize) {
        Map<String, Object> payload = command("new_collection");
        payload.put("min_size", minSize);
        if (maxSize != null) payload.put("max_size", maxSize);
        Object result = sendCommand(payload);
        return ((Number) result).longValue();
    }

    @Override
    public boolean collectionMore(long collectionId) {
        Map<String, Object> payload = command("collection_more");
        payload.put("collection_id", collectionId);
        Object result = sendCommand(payload);
        return (Boolean) result;
    }

    @Override
    public void collectionReject(long collectionId) {
        Map<String, Object> payload = command("collection_reject");
        payload.put("collection_id", collectionId);
        sendCommand(payload);
    }

    @Override
    public long newPool() {
        Object result = sendCommand(command("new_pool"));
        return ((Number) result).longValue();
    }

    @Override
    public long poolAdd(long poolId) {
        Map<String, Object> payload = command("pool_add");
        payload.put("pool_id", poolId);
        Object result = sendCommand(payload);
        return ((Number) result).longValue();
    }

    @Override
    public long poolGenerate(long poolId, boolean consume) {
        Map<String, Object> payload = command("pool_generate");
        payload.put("pool_id", poolId);
        payload.put("consume", consume);
        Object result = sendCommand(payload);
        return ((Number) result).longValue();
    }

    @Override
    public void markComplete(String status, String origin) {
        Map<String, Object> payload = command("mark_complete");
        payload.put("status", status);
        payload.put("origin", origin); // null is serialized as CBOR null
        try {
            stream.requestCbor(payload);
        } catch (IOException ignored) {
            // Errors during mark_complete are swallowed.
        }
        stream.close();
    }

    @Override
    public boolean testAborted() {
        return aborted.get();
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    /** Build a base payload map with the "command" field set. */
    private static Map<String, Object> command(String name) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("command", name);
        return map;
    }

    /**
     * Send a command, receive the reply, and return the result value.
     * Maps server-side error types to the appropriate sentinel exceptions.
     */
    private Object sendCommand(Map<String, Object> payload) {
        if (aborted.get()) throw new StopTestException();
        try {
            return stream.requestCbor(payload);
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("UnsatisfiedAssumption")) {
                throw new AssumeFailedException();
            }
            if (msg.contains("overflow")
                    || msg.contains("StopTest")
                    || msg.contains("stream is closed")) {
                stream.markClosed();
                aborted.set(true);
                throw new StopTestException();
            }
            // Flaky test detection: treat as stop
            if (msg.contains("FlakyStrategyDefinition") || msg.contains("FlakyReplay")) {
                stream.markClosed();
                aborted.set(true);
                throw new StopTestException();
            }
            throw new RuntimeException("Hegel server error: " + msg, e);
        }
    }
}
