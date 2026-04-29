package com.aaroneline.backend;

/**
 * Thrown when the server signals that the test case should stop (overflow /
 * StopTest). Not a real error — used as a flow-control mechanism.
 */
public final class StopTestException extends RuntimeException {
    public StopTestException() {
        // Suppress stack trace capture: this is flow-control, not a bug.
        super("__HEGEL_STOP_TEST", null, true, false);
    }
}
