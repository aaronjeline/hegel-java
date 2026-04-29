package io.hegel.generators;

/** Label constants for span grouping. Must match the Rust client labels. */
public final class SpanLabels {
    private SpanLabels() {}

    public static final long LIST         =  1L;
    public static final long LIST_ELEMENT =  2L;
    public static final long SET          =  3L;
    public static final long SET_ELEMENT  =  4L;
    public static final long MAP          =  5L;
    public static final long MAP_ENTRY    =  6L;
    public static final long TUPLE        =  7L;
    public static final long ONE_OF       =  8L;
    public static final long OPTIONAL     =  9L;
    public static final long FLAT_MAP     = 11L;
    public static final long FILTER       = 12L;
    public static final long MAPPED       = 13L;
    public static final long SAMPLED_FROM = 14L;
}
