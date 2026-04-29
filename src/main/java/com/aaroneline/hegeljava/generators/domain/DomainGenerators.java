package com.aaroneline.hegeljava.generators.domain;

import com.aaroneline.hegeljava.generators.BasicGenerator;

import java.util.LinkedHashMap;
import java.util.Map;

/** Domain-specific string generators (email, URL, IP addresses, etc.). */
public final class DomainGenerators {
    private DomainGenerators() {}

    // ── Email ──────────────────────────────────────────────────────────────────

    /** Generates RFC 5322 email address strings. */
    public static final class EmailGenerator extends BasicGenerator<String> {
        public EmailGenerator() {}

        @Override
        public Map<String, Object> schema() {
            return Map.of("type", "email");
        }

        @Override
        public String parseRaw(Object raw) {
            return asString(raw);
        }
    }

    // ── URL ────────────────────────────────────────────────────────────────────

    /** Generates HTTP/HTTPS URL strings. */
    public static final class UrlGenerator extends BasicGenerator<String> {
        public UrlGenerator() {}

        @Override
        public Map<String, Object> schema() {
            return Map.of("type", "url");
        }

        @Override
        public String parseRaw(Object raw) {
            return asString(raw);
        }
    }

    // ── Domain name ────────────────────────────────────────────────────────────

    /** Generates fully qualified domain name strings. */
    public static final class DomainGenerator extends BasicGenerator<String> {
        private final Integer maxLength;

        public DomainGenerator(Integer maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public Map<String, Object> schema() {
            if (maxLength == null) return Map.of("type", "domain");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "domain");
            m.put("max_length", maxLength);
            return m;
        }

        @Override
        public String parseRaw(Object raw) {
            return asString(raw);
        }
    }

    // ── IPv4 / IPv6 ────────────────────────────────────────────────────────────

    /** Generates IPv4 or IPv6 address strings depending on the {@code schemaType} passed at construction. */
    public static final class IpAddressGenerator extends BasicGenerator<String> {
        private final String schemaType; // "ipv4" or "ipv6"

        public IpAddressGenerator(String schemaType) {
            this.schemaType = schemaType;
        }

        @Override
        public Map<String, Object> schema() {
            return Map.of("type", schemaType);
        }

        @Override
        public String parseRaw(Object raw) {
            return asString(raw);
        }
    }

    // ── Date/Time ──────────────────────────────────────────────────────────────

    /** Generates ISO 8601 date strings, e.g. {@code "2024-03-15"}. */
    public static final class DateGenerator extends BasicGenerator<String> {
        public DateGenerator() {}

        @Override
        public Map<String, Object> schema() {
            return Map.of("type", "date");
        }

        @Override
        public String parseRaw(Object raw) {
            return asString(raw);
        }
    }

    /** Generates ISO 8601 time strings, e.g. {@code "14:30:00"}. */
    public static final class TimeGenerator extends BasicGenerator<String> {
        public TimeGenerator() {}

        @Override
        public Map<String, Object> schema() {
            return Map.of("type", "time");
        }

        @Override
        public String parseRaw(Object raw) {
            return asString(raw);
        }
    }

    /** Generates ISO 8601 datetime strings, e.g. {@code "2024-03-15T14:30:00"}. */
    public static final class DateTimeGenerator extends BasicGenerator<String> {
        public DateTimeGenerator() {}

        @Override
        public Map<String, Object> schema() {
            return Map.of("type", "datetime");
        }

        @Override
        public String parseRaw(Object raw) {
            return asString(raw);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String asString(Object raw) {
        if (raw instanceof String s) return s;
        if (raw instanceof byte[] b) return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        throw new IllegalArgumentException("Expected string, got: " + raw);
    }
}
