package com.aaroneline.backend;

/**
 * Thrown when {@code tc.assume(false)} or {@code tc.reject()} is called.
 * Not a real error — signals that this test case should be discarded as INVALID.
 */
public final class AssumeFailedException extends RuntimeException {
    public AssumeFailedException() {
        // Suppress stack trace capture: this is flow-control, not a bug.
        super("__HEGEL_ASSUME_FAIL", null, true, false);
    }
}
