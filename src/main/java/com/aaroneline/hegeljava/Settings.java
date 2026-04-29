package com.aaroneline.hegeljava;

import java.util.List;

/** Configuration for a Hegel test run. */
public final class Settings {

    /** Maximum number of test cases to run. */
    public final int testCases;
    /** Random seed. {@code null} means "let the server choose." */
    public final Long seed;
    /** Health check names to suppress (e.g. {@code "filter_too_much"}). */
    public final List<String> suppressHealthCheck;
    /**
     * Path to the shrinking database directory, or {@code null} to disable.
     * Defaults to {@code ".hegel/java-db"}.
     */
    public final String database;
    /**
     * If {@code true}, always run the same deterministic sequence of test cases
     * regardless of random seed.
     */
    public final boolean derandomize;

    /**
     * @param testCases           maximum number of test cases to run
     * @param seed                random seed, or {@code null} to let the server choose
     * @param suppressHealthCheck health check names to suppress
     * @param database            path to the shrinking database directory,
     *                            or {@code null} to disable
     * @param derandomize         if {@code true}, run a deterministic fixed sequence
     */
    public Settings(int testCases, Long seed, List<String> suppressHealthCheck,
                    String database, boolean derandomize) {
        this.testCases = testCases;
        this.seed = seed;
        this.suppressHealthCheck = List.copyOf(suppressHealthCheck);
        this.database = database;
        this.derandomize = derandomize;
    }

    /**
     * Build a {@code Settings} from a {@link HegelTest} annotation.
     * Uses {@code ".hegel/java-db"} as the default database path.
     */
    public static Settings from(HegelTest annotation) {
        long rawSeed = annotation.seed();
        Long seed = (rawSeed == Long.MIN_VALUE) ? null : rawSeed;
        return new Settings(
                annotation.testCases(),
                seed,
                List.of(annotation.suppressHealthCheck()),
                ".hegel/java-db",
                false);
    }
}
