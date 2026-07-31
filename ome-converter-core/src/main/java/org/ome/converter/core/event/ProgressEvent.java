package org.ome.converter.core.event;

public record ProgressEvent(
    String jobId,
    double percentage,
    long tilesProcessed,
    long totalTiles,
    String currentTask,
    boolean completed,
    boolean failed
) {}
