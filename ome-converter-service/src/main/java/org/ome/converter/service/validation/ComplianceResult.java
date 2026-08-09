package org.ome.converter.service.validation;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ComplianceResult(
    Path datasetPath,
    String datasetName,
    String detectedOmeVersion,
    String detectedZarrVersion,
    OverallStatus overallStatus,
    Map<ComplianceCategory, ComplianceSeverity> categoryStatuses,
    List<ComplianceIssue> issues,
    int errorCount,
    int warningCount,
    int infoCount,
    int passCount,
    int totalChecks,
    Path htmlReportPath
) {
    public boolean isPass() {
        return overallStatus == OverallStatus.PASS;
    }
}
