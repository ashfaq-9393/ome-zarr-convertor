package org.ome.converter.core.model;

import java.nio.file.Path;

public record ConversionRequest(
    String jobId,
    Path sourceFile,
    Path targetDestinationDirectory,
    ChunkSpec chunkSpec,
    boolean preserveVendorMetadata,
    int threadCount,
    OmeZarrVersion targetVersion
) {
    public ConversionRequest {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId cannot be blank");
        }
        if (sourceFile == null) {
            throw new IllegalArgumentException("sourceFile cannot be null");
        }
        if (targetDestinationDirectory == null) {
            throw new IllegalArgumentException("targetDestinationDirectory cannot be null");
        }
        if (chunkSpec == null) {
            chunkSpec = ChunkSpec.defaultSpec();
        }
        if (targetVersion == null) {
            targetVersion = OmeZarrVersion.OME_ZARR_0_5;
        }
    }

    public ConversionRequest(
        String jobId,
        Path sourceFile,
        Path targetDestinationDirectory,
        ChunkSpec chunkSpec,
        boolean preserveVendorMetadata,
        int threadCount
    ) {
        this(jobId, sourceFile, targetDestinationDirectory, chunkSpec, preserveVendorMetadata, threadCount, OmeZarrVersion.OME_ZARR_0_5);
    }
}
