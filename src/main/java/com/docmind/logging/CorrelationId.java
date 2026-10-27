package com.docmind.logging;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A correlation id ties together all log lines that belong to one upload or
 * question, from the HTTP request through Kafka to the background consumer.
 * It is kept in the logging MDC under {@link #MDC_KEY}.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    // ids from clients end up in log lines, so only accept short, plain values
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {
    }

    public static String fromOrNew(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
    }
}
