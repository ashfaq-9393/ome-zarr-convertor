package org.ome.converter.core.model;

import java.nio.file.Path;
import java.time.Duration;

public record ConversionResult(
    String jobId,
    Path outputZarrPath,
    Status status,
    long totalTilesConverted,
    long totalBytesWritten,
    Duration executionDuration,
    String errorMessage,
    Throwable cause,
    GapAnalysisResult gapAnalysisResult,
    ImageMetadata standardMetadata,
    VendorMetadata vendorMetadata
) {
    public enum Status {
        SUCCESS,
        FAILED,
        CANCELLED
    }

    public ConversionResult(
        String jobId,
        Path outputZarrPath,
        Status status,
        long totalTilesConverted,
        long totalBytesWritten,
        Duration executionDuration,
        String errorMessage,
        Throwable cause
    ) {
        this(jobId, outputZarrPath, status, totalTilesConverted, totalBytesWritten, executionDuration, errorMessage, cause, null, null, null);
    }

    public static ConversionResult success(String jobId, Path outputPath, long tiles, long bytes, Duration duration) {
        return new ConversionResult(jobId, outputPath, Status.SUCCESS, tiles, bytes, duration, null, null, null, null, null);
    }

    public static ConversionResult successWithMetadata(String jobId, Path outputPath, long tiles, long bytes, Duration duration, ImageMetadata standardMeta, VendorMetadata vendorMeta) {
        return new ConversionResult(jobId, outputPath, Status.SUCCESS, tiles, bytes, duration, null, null, null, standardMeta, vendorMeta);
    }

    public static ConversionResult successWithAnalysis(String jobId, Path outputPath, long tiles, long bytes, Duration duration, GapAnalysisResult gapResult) {
        return new ConversionResult(jobId, outputPath, Status.SUCCESS, tiles, bytes, duration, null, null, gapResult, null, null);
    }

    public static ConversionResult failure(String jobId, Path outputPath, String message, Throwable cause, Duration duration) {
        return new ConversionResult(jobId, outputPath, Status.FAILED, 0, 0, duration, message, cause, null, null, null);
    }
}
