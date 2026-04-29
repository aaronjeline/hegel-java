package com.aaroneline;

import com.aaroneline.protocol.Connection;
import com.aaroneline.protocol.HegelStream;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Manages the persistent connection to the Hegel server subprocess.
 *
 * <p>A single session is shared for the entire test process lifetime.
 * A new session is created automatically if the server exits unexpectedly.
 */
public final class HegelSession {

    static final String HANDSHAKE_PAYLOAD = "hegel_handshake_start";
    static final String[] SUPPORTED_VERSIONS = {"0.10", "0.10"}; // [min, max]
    static final String SERVER_COMMAND_ENV = "HEGEL_SERVER_COMMAND";
    static final String HEGEL_SERVER_VERSION = "0.4.7";
    static final String SERVER_DIR = ".hegel";

    private static final Object SESSION_LOCK = new Object();
    private static volatile HegelSession instance;

    final Connection connection;
    /** Shared control stream — all callers must hold controlLock while using it. */
    final HegelStream controlStream;
    final Object controlLock = new Object();
    private final Process serverProcess;

    // ── Singleton access ───────────────────────────────────────────────────────

    /** Returns the live session, creating a new one if the server has exited. */
    public static HegelSession get() {
        HegelSession s = instance;
        if (s != null && !s.connection.serverHasExited()) return s;
        synchronized (SESSION_LOCK) {
            s = instance;
            if (s == null || s.connection.serverHasExited()) {
                instance = s = create();
            }
            return s;
        }
    }

    // ── Initialization ─────────────────────────────────────────────────────────

    private static HegelSession create() {
        ensureServerDir();
        ProcessBuilder pb = resolveCommand();
        pb.redirectErrorStream(false);
        pb.redirectError(new File(SERVER_DIR + "/server.log"));

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to spawn Hegel server. " +
                    "Make sure 'hegel' is on PATH or set " + SERVER_COMMAND_ENV + ".", e);
        }

        Connection conn = new Connection(process.getInputStream(), process.getOutputStream());
        HegelStream control = conn.controlStream();

        // Perform handshake (raw ASCII, not CBOR)
        try {
            byte[] hsBytes = HANDSHAKE_PAYLOAD.getBytes(StandardCharsets.US_ASCII);
            int msgId = control.sendRequest(hsBytes);
            byte[] reply = control.receiveReply(msgId);
            String decoded = new String(reply, StandardCharsets.US_ASCII);
            validateHandshake(decoded, process);
        } catch (IOException e) {
            process.destroyForcibly();
            throw new RuntimeException("Hegel handshake failed: " + e.getMessage(), e);
        }

        // Monitor thread: detects server exit.
        Thread monitor = new Thread(() -> {
            while (true) {
                boolean exited;
                try {
                    exited = process.waitFor(10, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (exited) {
                    conn.markServerExited();
                    break;
                }
            }
        }, "hegel-monitor");
        monitor.setDaemon(true);
        monitor.start();

        return new HegelSession(conn, control, process);
    }

    private HegelSession(Connection connection, HegelStream controlStream, Process serverProcess) {
        this.connection = connection;
        this.controlStream = controlStream;
        this.serverProcess = serverProcess;
    }

    // ── run_test ───────────────────────────────────────────────────────────────

    /**
     * Send a {@code run_test} command on the control stream.
     *
     * @param testStreamId the stream ID for this test run (must be a client stream ID)
     * @param settings     test configuration
     * @param databaseKey  stable key for the shrinking database
     */
    @SuppressWarnings("unchecked")
    public void runTest(int testStreamId, Settings settings, byte[] databaseKey) {
        java.util.Map<String, Object> msg = new java.util.LinkedHashMap<>();
        msg.put("command", "run_test");
        msg.put("stream_id", testStreamId);
        msg.put("test_cases", settings.testCases);
        msg.put("seed", settings.seed); // null → CBOR null
        msg.put("database_key", databaseKey);
        msg.put("derandomize", settings.derandomize);
        if (settings.database != null) {
            msg.put("database", settings.database);
        } else {
            msg.put("database", null);
        }
        if (!settings.suppressHealthCheck.isEmpty()) {
            msg.put("suppress_health_check", settings.suppressHealthCheck);
        }

        try {
            synchronized (controlLock) {
                controlStream.requestCbor(msg);
            }
        } catch (IOException e) {
            throw new RuntimeException("run_test failed: " + e.getMessage(), e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static ProcessBuilder resolveCommand() {
        String override = System.getenv(SERVER_COMMAND_ENV);
        if (override != null) {
            return new ProcessBuilder(override, "--stdio");
        }
        // Try 'hegel' on PATH
        if (isOnPath("hegel")) {
            return new ProcessBuilder("hegel", "--stdio");
        }
        // Fallback: uvx
        return new ProcessBuilder("uvx",
                "--from", "hegel-core==" + HEGEL_SERVER_VERSION,
                "hegel", "--stdio");
    }

    private static boolean isOnPath(String command) {
        try {
            Process p = new ProcessBuilder(command, "--version")
                    .redirectErrorStream(true).start();
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            p.destroyForcibly();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void validateHandshake(String reply, Process process) {
        String prefix = "Hegel/";
        if (!reply.startsWith(prefix)) {
            process.destroyForcibly();
            throw new RuntimeException("Unexpected handshake reply: " + reply);
        }
        String version = reply.substring(prefix.length()).trim();
        if (!versionInRange(version, SUPPORTED_VERSIONS[0], SUPPORTED_VERSIONS[1])) {
            process.destroyForcibly();
            throw new RuntimeException(
                    "Protocol version mismatch: server is " + version +
                    ", client supports " + SUPPORTED_VERSIONS[0] +
                    " through " + SUPPORTED_VERSIONS[1]);
        }
    }

    private static boolean versionInRange(String version, String min, String max) {
        try {
            int[] v = parseVersion(version);
            int[] lo = parseVersion(min);
            int[] hi = parseVersion(max);
            return compare(v, lo) >= 0 && compare(v, hi) <= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int[] parseVersion(String s) {
        String[] parts = s.split("\\.");
        return new int[]{ Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
    }

    private static int compare(int[] a, int[] b) {
        int c = Integer.compare(a[0], b[0]);
        return c != 0 ? c : Integer.compare(a[1], b[1]);
    }

    private static void ensureServerDir() {
        new File(SERVER_DIR).mkdirs();
    }
}
