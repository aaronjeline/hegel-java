package com.aaroneline.hegeljava.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single multiplexed stream within a {@link Connection}.
 *
 * <p>Not thread-safe. Intended to be used from a single thread at a time
 * (the thread driving the test case). The underlying {@link Connection}
 * handles synchronized packet delivery.
 */
public final class HegelStream {

    // stream_close sentinel values per the protocol spec
    private static final byte[] CLOSE_STREAM_PAYLOAD = { (byte) 0xFE };
    private static final int CLOSE_STREAM_MESSAGE_ID = 0x7FFFFFFF; // (1 << 31) - 1

    public static final ObjectMapper CBOR = new ObjectMapper(new CBORFactory());

    public final int streamId;
    private final Connection connection;
    private final LinkedBlockingQueue<Packet> incomingQueue;
    private final AtomicInteger nextMessageId = new AtomicInteger(1);

    // Buffered incoming packets waiting to be consumed.
    private final Map<Integer, byte[]> pendingReplies = new HashMap<>();
    private final List<Packet> pendingRequests = new ArrayList<>();

    private volatile boolean closed = false;

    HegelStream(int streamId, Connection connection, LinkedBlockingQueue<Packet> incomingQueue) {
        this.streamId = streamId;
        this.connection = connection;
        this.incomingQueue = incomingQueue;
    }

    // ── Low-level send/receive ─────────────────────────────────────────────────

    /** Send raw bytes as a request packet; returns the allocated message ID. */
    public int sendRequest(byte[] payload) throws IOException {
        checkOpen();
        int msgId = nextMessageId.getAndIncrement();
        connection.sendPacket(new Packet(streamId, msgId, false, payload));
        return msgId;
    }

    /** Receive the reply for a previously sent request (blocks until available). */
    public byte[] receiveReply(int msgId) throws IOException {
        while (true) {
            byte[] buffered = pendingReplies.remove(msgId);
            if (buffered != null) return buffered;
            checkOpen();
            receiveOnePacket();
        }
    }

    /**
     * Receive the next request sent by the server on this stream (blocks).
     * Returns (messageId, payload).
     */
    public Map.Entry<Integer, byte[]> receiveRequest() throws IOException {
        while (true) {
            if (!pendingRequests.isEmpty()) {
                Packet p = pendingRequests.remove(0);
                return Map.entry(p.messageId, p.payload);
            }
            checkOpen();
            receiveOnePacket();
        }
    }

    /** Send a reply packet for a server-sent request. */
    public void writeReply(int msgId, byte[] payload) throws IOException {
        connection.sendPacket(new Packet(streamId, msgId, true, payload));
    }

    // ── CBOR convenience ───────────────────────────────────────────────────────

    /**
     * Serialize {@code payload} to CBOR, send as a request, wait for the reply,
     * deserialize the response, and return the {@code result} field.
     *
     * <p>If the response contains an {@code error} field, throws an
     * {@link IOException} whose message includes the error type.
     */
    @SuppressWarnings("unchecked")
    public Object requestCbor(Map<String, Object> payload) throws IOException {
        byte[] reqBytes = CBOR.writeValueAsBytes(payload);
        int msgId = sendRequest(reqBytes);
        byte[] respBytes = receiveReply(msgId);

        Map<String, Object> response = CBOR.readValue(respBytes, Map.class);
        if (response.containsKey("error")) {
            String errorType = (String) response.getOrDefault("type", "");
            Object errorMsg = response.get("error");
            throw new IOException("Server error (" + errorType + "): " + errorMsg);
        }
        if (response.containsKey("result")) {
            return response.get("result");
        }
        return response;
    }

    // ── Stream lifecycle ───────────────────────────────────────────────────────

    /** Mark as closed without sending a close packet (used on StopTest/overflow). */
    public void markClosed() {
        closed = true;
    }

    /** Send the stream-close packet and unregister from the connection. */
    public void close() {
        markClosed();
        connection.unregisterStream(streamId);
        try {
            connection.sendPacket(
                    new Packet(streamId, CLOSE_STREAM_MESSAGE_ID, false, CLOSE_STREAM_PAYLOAD));
        } catch (IOException ignored) {
            // Server may already be gone; nothing to do.
        }
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private void checkOpen() throws IOException {
        if (closed) throw new IOException("stream is closed");
    }

    private void receiveOnePacket() throws IOException {
        Packet p;
        try {
            p = incomingQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for packet", e);
        }
        if (p == Connection.POISON_PILL) {
            // Re-insert so other threads waiting on this queue also unblock.
            incomingQueue.offer(Connection.POISON_PILL);
            if (connection.serverHasExited()) {
                throw new IOException(
                        "The hegel server process exited unexpectedly. " +
                        "See .hegel/server.log for diagnostic information.");
            }
            throw new IOException("stream disconnected");
        }
        if (p.isReply) {
            pendingReplies.put(p.messageId, p.payload);
        } else {
            pendingRequests.add(p);
        }
    }
}
