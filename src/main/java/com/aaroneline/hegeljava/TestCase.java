package com.aaroneline.hegeljava;

import com.aaroneline.hegeljava.backend.DataSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a single test case execution.
 *
 * <p>Passed as a parameter to every {@link HegelTest} test method. Use
 * {@link #draw(Generator)} to request generated values, {@link #assume(boolean)}
 * to filter out uninteresting cases, and {@link #note(String)} to attach
 * diagnostic information to failures.
 *
 * <p>Not thread-safe. Intended to be used from the single thread running
 * the test method.
 */
public final class TestCase {

    private final DataSource dataSource;
    private final boolean finalRun;
    private final Consumer<String> outputSink;
    /** Collected draw/note lines during the final replay, for inclusion in the failure report. */
    private final List<String> counterexampleLines;

    /**
     * @param dataSource the backend providing generated values
     * @param finalRun   {@code true} during the final replay of a failing example
     * @param outputSink where to write draw/note output ({@code System.err} during final replay)
     */
    public TestCase(DataSource dataSource, boolean finalRun, Consumer<String> outputSink) {
        this.dataSource = dataSource;
        this.finalRun = finalRun;
        this.outputSink = outputSink;
        this.counterexampleLines = finalRun ? new ArrayList<>() : Collections.emptyList();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Draw a value from the given generator.
     *
     * <p>During the final replay the drawn value is recorded for the failure report.
     */
    public <T> T draw(Generator<T> generator) {
        T value = generator.draw(this);
        if (finalRun) {
            counterexampleLines.add("Draw: " + value);
        }
        return value;
    }

    /**
     * Filter out the current test case if {@code condition} is {@code false}.
     *
     * <p>The test case is marked INVALID and discarded; a fresh test case will
     * be generated instead.
     */
    public void assume(boolean condition) {
        if (!condition) reject();
    }

    /**
     * Unconditionally reject the current test case as INVALID.
     * This method never returns normally.
     */
    public void reject() {
        throw new com.aaroneline.hegeljava.backend.AssumeFailedException();
    }

    /**
     * Attach a diagnostic message to this test case.
     *
     * <p>The message is recorded only during the final replay of a failing
     * example and included in the failure report.
     */
    public void note(String message) {
        if (finalRun) {
            counterexampleLines.add("Note: " + message);
        }
    }

    // ── Package-private helpers ────────────────────────────────────────────────

    /**
     * Draw silently (no output during final replay).
     * Used internally by generators that draw infrastructure values.
     */
    public <T> T drawSilent(Generator<T> generator) {
        return generator.draw(this);
    }

    /** Returns the underlying data source (used by generators). */
    public DataSource getDataSource() {
        return dataSource;
    }

    /** Returns {@code true} if this is the final replay of a failing example. */
    public boolean isFinalRun() {
        return finalRun;
    }

    /** Returns the draw/note lines recorded during this final replay. */
    public List<String> getCounterexampleLines() {
        return Collections.unmodifiableList(counterexampleLines);
    }
}
