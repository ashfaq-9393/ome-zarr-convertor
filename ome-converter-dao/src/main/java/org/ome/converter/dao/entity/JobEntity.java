package org.ome.converter.dao.entity;

import java.time.Instant;

public record JobEntity(
    String id,
    String sourcePath,
    String destinationPath,
    String status,
    long tilesProcessed,
    long bytesWritten,
    Instant startTime,
    Instant endTime,
    String errorMessage
) {}
