package io.hegel.backend;

import java.util.Map;

/**
 * Abstracts all communication with the data source (the Hegel server).
 *
 * <p>Each method corresponds to a protocol command. Methods that can be cut
 * short by data exhaustion or assumption rejection throw the appropriate
 * sentinel exception rather than returning an error.
 */
public interface DataSource {

    /**
     * Request a generated value matching the given schema.
     *
     * @param schema CBOR-serializable map describing the generator schema
     *               (e.g. {@code {"type": "integer", "min_value": 0}})
     * @return the raw generated value (deserialized from CBOR)
     */
    Object generate(Map<String, Object> schema);

    /** Begin a labeled span (groups related generation calls for shrinking). */
    void startSpan(long label);

    /** End the current span. If {@code discard} is true, the span is excluded from shrinking. */
    void stopSpan(boolean discard);

    /**
     * Create a new server-managed collection for dynamic-sized generation.
     *
     * @return an opaque collection ID to pass to {@link #collectionMore} and
     *         {@link #collectionReject}
     */
    long newCollection(long minSize, Long maxSize);

    /**
     * Ask whether the collection should produce another element.
     *
     * @return {@code true} if another element should be drawn
     */
    boolean collectionMore(long collectionId);

    /** Reject the most recently drawn element from the collection. */
    void collectionReject(long collectionId);

    /** Create a new variable pool. Returns an opaque pool ID. */
    long newPool();

    /** Add a new variable to a pool. Returns the new variable ID. */
    long poolAdd(long poolId);

    /**
     * Draw a variable ID from a pool.
     *
     * @param consume if {@code true}, the variable is removed from the pool
     */
    long poolGenerate(long poolId, boolean consume);

    /**
     * Signal the server that the test case is complete.
     *
     * @param status one of {@code "VALID"}, {@code "INVALID"}, {@code "INTERESTING"}
     * @param origin optional description of the failure location (for INTERESTING)
     */
    void markComplete(String status, String origin);

    /**
     * Returns {@code true} if a previous request caused the test case to abort
     * (StopTest / overflow). When true, {@link #markComplete} must not be called.
     */
    boolean testAborted();
}
