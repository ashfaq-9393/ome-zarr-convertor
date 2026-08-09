package org.ome.converter.service.validation;

import org.ome.converter.service.validation.report.ComplianceReportGenerator;
import org.ome.converter.service.validation.v04.OmeZarrV04ComplianceValidator;
import org.ome.converter.service.validation.v05.OmeZarrV05ComplianceValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

public class OmeZarrComplianceService {
    private static final Logger log = LoggerFactory.getLogger(OmeZarrComplianceService.class);

    private final OmeZarrVersionDetector versionDetector = new OmeZarrVersionDetector();
    private final OmeZarrV04ComplianceValidator v04Validator = new OmeZarrV04ComplianceValidator();
    private final OmeZarrV05ComplianceValidator v05Validator = new OmeZarrV05ComplianceValidator();
    private final ComplianceReportGenerator reportGenerator = new ComplianceReportGenerator();

    public ComplianceResult validateDataset(Path zarrRoot) {
        String datasetName = zarrRoot != null && zarrRoot.getFileName() != null ? zarrRoot.getFileName().toString() : "dataset";

        log.info("Starting OME-Zarr compliance validation for dataset at {}", zarrRoot);

        // 1. Detect Version
        OmeZarrVersionDetector.DetectionResult detection = versionDetector.detectVersion(zarrRoot);
        String detectedOmeVer = detection.omeZarrVersion();
        String detectedZarrVer = detection.zarrFormatVersion();

        List<ComplianceIssue> issues = new ArrayList<>();

        if ("VERSION_CONFLICT".equals(detectedOmeVer)) {
            issues.add(ComplianceIssue.error(ComplianceCategory.VERSION, "OME-NGFF Spec", "Conflicting OME-NGFF version metadata detected across hierarchy", "Consistent version tag", detection.details()));
        } else if ("UNKNOWN".equals(detectedOmeVer)) {
            issues.add(ComplianceIssue.error(ComplianceCategory.VERSION, "OME-NGFF Spec", "Could not reliably determine OME-NGFF version metadata", "0.4 or 0.5 version tag", detection.details()));
        } else if ("0.4".equals(detectedOmeVer)) {
            issues.addAll(v04Validator.validate(zarrRoot));
        } else if ("0.5".equals(detectedOmeVer)) {
            issues.addAll(v05Validator.validate(zarrRoot));
        }

        // 2. Count Severities & Calculate Category Map
        int passCount = 0;
        int warnCount = 0;
        int errCount = 0;
        int infoCount = 0;

        Map<ComplianceCategory, ComplianceSeverity> categoryMap = new EnumMap<>(ComplianceCategory.class);
        for (ComplianceCategory cat : ComplianceCategory.values()) {
            categoryMap.put(cat, ComplianceSeverity.PASS);
        }

        for (ComplianceIssue issue : issues) {
            switch (issue.severity()) {
                case PASS -> passCount++;
                case WARNING -> {
                    warnCount++;
                    updateCategorySeverity(categoryMap, issue.category(), ComplianceSeverity.WARNING);
                }
                case ERROR -> {
                    errCount++;
                    updateCategorySeverity(categoryMap, issue.category(), ComplianceSeverity.ERROR);
                }
                case INFO -> {
                    infoCount++;
                    if (categoryMap.get(issue.category()) == ComplianceSeverity.PASS && issue.problem().contains("Not Present")) {
                        updateCategorySeverity(categoryMap, issue.category(), ComplianceSeverity.INFO);
                    }
                }
            }
        }

        // 3. Determine Overall Status
        OverallStatus overallStatus;
        if ("VERSION_CONFLICT".equals(detectedOmeVer)) {
            overallStatus = OverallStatus.VERSION_CONFLICT;
        } else if ("UNKNOWN".equals(detectedOmeVer)) {
            overallStatus = OverallStatus.UNKNOWN;
        } else if (errCount > 0) {
            overallStatus = OverallStatus.FAIL;
        } else if (warnCount > 0) {
            overallStatus = OverallStatus.WARNING;
        } else {
            overallStatus = OverallStatus.PASS;
        }

        // 4. Generate HTML Report
        Path reportPath = reportGenerator.generateReport(
            zarrRoot,
            detectedOmeVer,
            detectedZarrVer,
            overallStatus,
            categoryMap,
            issues,
            passCount,
            warnCount,
            errCount,
            infoCount
        );

        ComplianceResult result = new ComplianceResult(
            zarrRoot,
            datasetName,
            detectedOmeVer,
            detectedZarrVer,
            overallStatus,
            categoryMap,
            issues,
            errCount,
            warnCount,
            infoCount,
            passCount,
            issues.size(),
            reportPath
        );

        log.info("Finished compliance check for {}. Overall Status: {}, Errors: {}, Warnings: {}, Info: {}", datasetName, overallStatus, errCount, warnCount, infoCount);
        return result;
    }

    private void updateCategorySeverity(Map<ComplianceCategory, ComplianceSeverity> map, ComplianceCategory category, ComplianceSeverity severity) {
        ComplianceSeverity existing = map.getOrDefault(category, ComplianceSeverity.PASS);
        if (severity == ComplianceSeverity.ERROR) {
            map.put(category, ComplianceSeverity.ERROR);
        } else if (severity == ComplianceSeverity.WARNING && existing != ComplianceSeverity.ERROR) {
            map.put(category, ComplianceSeverity.WARNING);
        } else if (severity == ComplianceSeverity.INFO && existing == ComplianceSeverity.PASS) {
            map.put(category, ComplianceSeverity.INFO);
        }
    }
}
