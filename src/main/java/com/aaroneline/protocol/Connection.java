package com.aaroneline.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multiplexed connection between the client and the Hegel server.
 *
 * <p>A background reader thread continuously reads packets from the server and
 * dispatches them to the appropriate stream's queue. Multiple streams share the
 * same underlying I/O but are otherwise independent.
 *
 * <p>Thread-safe: packet sending is synchronized on the writer lock; stream
 * queues are {@link LinkedBlockingQueue} instances fed by the reader thread.
 */
public final class Connection {

    /** Sentinel placed into every stream queue when the server exits. */
    static final Packet POISON_PILL = new Packet(-1, 0, false, new byte[0]);

    private final OutputStream writer;
    private final Object writerLock = new Object();
    private final ConcurrentHashMap<Integer, LinkedBlockingQueue<Packet>> streamQueues =
            new ConcurrentHashMap<>();
    // Counter for allocating client stream IDs. Stream IDs are (counter << 1) | 1 (always odd).
    private final AtomicInteger nextStreamCounter = new AtomicInteger(1);
    private final AtomicBoolean serverExited = new AtomicBoolean(false);

    public Connection(InputStream reader, OutputStream writer) {
        this.writer = writer;
        Thread readerThread = new Thread(() -> {
            try {
                while (true) {
                    Packet p = Packet.read(reader);
                    LinkedBlockingQueue<Packet> queue = streamQueues.get(p.streamId);
                    if (queue != null) {
                        queue.offer(p);
                    }
                    // Packets for unknown streams are silently dropped.
                }
            } catch (IOException e) {
                markServerExited();
            }
        }, "hegel-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    // ── Stream management ──────────────────────────────────────────────────────

    /** Returns the control stream (stream ID 0). Must be called once per connection. */
    public HegelStream controlStream() {
        return registerStream(0);
    }

    /** Allocates a new client-owned stream (odd ID). */
    public HegelStream newStream() {
        int next = nextStreamCounter.getAndIncrement();
        int streamId = (next << 1) | 1; // S-bit = 1
        return registerStream(streamId);
    }

    /**
     * Connects to an existing server-allocated stream ID (as received in a
     * {@code test_case} event's {@code stream_id} field).
     */
    public HegelStream connectStream(int streamId) {
        return registerStream(streamId);
    }

    private HegelStream registerStream(int streamId) {
        LinkedBlockingQueue<Packet> queue = new LinkedBlockingQueue<>();
        streamQueues.put(streamId, queue);
        // If the server already exited, poison the queue immediately so
        // any blocking take() unblocks with the error sentinel.
        if (serverExited.get()) {
            queue.offer(POISON_PILL);
        }
        return new HegelStream(streamId, this, queue);
    }

    void unregisterStream(int streamId) {
        streamQueues.remove(streamId);
    }

    // ── Packet sending ─────────────────────────────────────────────────────────

    void sendPacket(Packet packet) throws IOException {
        synchronized (writerLock) {
            Packet.write(writer, packet);
        }
    }

    // ── Server exit ────────────────────────────────────────────────────────────

    /**
     * Signals that the server process has exited.
     * Inserts the poison-pill sentinel into every registered stream queue so
     * all threads blocked on {@link HegelStream#receiveReply} or
     * {@link HegelStream#receiveRequest} are unblocked with an error.
     */
    public void markServerExited() {
        serverExited.set(true);
        // Unblock all streams waiting for packets.
        for (LinkedBlockingQueue<Packet> q : streamQueues.values()) {
            q.offer(POISON_PILL);
        }
        streamQueues.clear();
    }

    /** Returns {@code true} if the server process has already exited. */
    public boolean serverHasExited() {
        return serverExited.get();
    }
}
