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
    Path htmlReportPath
) {
    public record GapAnalysisItemDetail(
        String originalKey,
        String originalValue,
        String status,
        String explanation
    ) {
        public String getOriginalKey() { return originalKey; }
        public String getOriginalValue() { return originalValue; }
        public String getStatus() { return status; }
        public String getExplanation() { return explanation; }
    }
}
