package org.ome.converter.core.model;

import java.nio.file.Path;
import java.util.List;

public record GapAnalysisResult(
    String datasetName,
    OmeZarrVersion targetVersion,
    int totalOriginalCount,
    int mappedCount,
    int vendorDumpedCount,
    int lossCount,
    List<GapAnalysisItemDetail> lostItems,
    List<GapAnalysisItemDetail> allItems,
    Path htmlReportPath,
    Path zarrRootPath
) {
    public GapAnalysisResult(
        String datasetName,
        OmeZarrVersion targetVersion,
        int totalOriginalCount,
        int mappedCount,
        int vendorDumpedCount,
        int lossCount,
        List<GapAnalysisItemDetail> lostItems,
        Path htmlReportPath
    ) {
        this(datasetName, targetVersion, totalOriginalCount, mappedCount, vendorDumpedCount, lossCount, lostItems, lostItems, htmlReportPath, null);
    }

    public GapAnalysisResult(
        String datasetName,
        OmeZarrVersion targetVersion,
        int totalOriginalCount,
        int mappedCount,
        int vendorDumpedCount,
        int lossCount,
        List<GapAnalysisItemDetail> lostItems,
        List<GapAnalysisItemDetail> allItems,
        Path htmlReportPath
    ) {
        this(datasetName, targetVersion, totalOriginalCount, mappedCount, vendorDumpedCount, lossCount, lostItems, allItems, htmlReportPath, null);
    }

    public record GapAnalysisItemDetail(
        String originalKey,
        String originalValue,
        String status,
        String convertedLocation,
        String convertedValue,
        String explanation
    ) {
        public GapAnalysisItemDetail(String originalKey, String originalValue, String status, String explanation) {
            this(originalKey, originalValue, status, deriveLocation(originalKey, status), deriveValue(originalValue, status), explanation);
        }

        public GapAnalysisItemDetail(String originalKey, String originalValue, String status, String convertedLocation, String convertedValue, String explanation) {
            this.originalKey = originalKey;
            this.originalValue = originalValue;
            this.status = status;
            this.convertedLocation = (convertedLocation != null && !convertedLocation.isBlank()) ? convertedLocation : deriveLocation(originalKey, status);
            this.convertedValue = (convertedValue != null && !convertedValue.isBlank()) ? convertedValue : deriveValue(originalValue, status);
            this.explanation = explanation;
        }

        private static String deriveLocation(String key, String status) {
            if (status == null) return "N/A (Unmapped)";
            String s = status.toUpperCase();
            if (s.contains("MAPPED")) return "zarr.json#/ome/series/0";
            if (s.contains("VENDOR") || s.contains("STRUCTURAL")) return "zarr.json#/vendor_metadata/" + (key != null ? key : "");
            return "N/A (Unmapped)";
        }

        private static String deriveValue(String val, String status) {
            if (status != null && status.toUpperCase().contains("LOSS")) {
                return "N/A";
            }
            return (val != null && !val.isBlank()) ? val : "N/A";
        }

        public String getOriginalKey() { return originalKey; }
        public String getOriginalValue() { return originalValue; }
        public String getStatus() { return status; }
        public String getConvertedLocation() { return convertedLocation; }
        public String getConvertedValue() { return convertedValue; }
        public String getExplanation() { return explanation; }
    }
}
