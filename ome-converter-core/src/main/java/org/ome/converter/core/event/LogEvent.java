package org.ome.converter.core.event;

import java.time.Instant;

public record LogEvent(
    String jobId,
    Level level,
    String message,
    Instant timestamp
) {
    public enum Level {
        INFO,
        WARN,
        ERROR,
        DEBUG
    }

    public static LogEvent info(String jobId, String message) {
        return new LogEvent(jobId, Level.INFO, message, Instant.now());
    }

    public static LogEvent warn(String jobId, String message) {
        return new LogEvent(jobId, Level.WARN, message, Instant.now());
    }

    public static LogEvent error(String jobId, String message) {
        return new LogEvent(jobId, Level.ERROR, message, Instant.now());
    }
}
