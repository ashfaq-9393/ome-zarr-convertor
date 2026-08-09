package org.ome.converter.service.validation.report;

import org.ome.converter.service.validation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ComplianceReportGenerator {
    private static final Logger log = LoggerFactory.getLogger(ComplianceReportGenerator.class);

    public Path generateReport(
        Path zarrRoot,
        String detectedOmeVer,
        String detectedZarrVer,
        OverallStatus overallStatus,
        Map<ComplianceCategory, ComplianceSeverity> categoryStatuses,
        List<ComplianceIssue> issues,
        int passCount,
        int warnCount,
        int errCount,
        int infoCount
    ) {
        String datasetName = zarrRoot.getFileName() != null ? zarrRoot.getFileName().toString() : "dataset.ome.zarr";
        Path reportFile = zarrRoot.resolve(datasetName + "_compliance_report.html");

        try {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n")
                .append("<html lang=\"en\">\n")
                .append("<head>\n")
                .append("<meta charset=\"UTF-8\">\n")
                .append("<title>OME-Zarr Compliance Report - ").append(escapeHtml(datasetName)).append("</title>\n")
                .append("<style>\n")
                .append("  body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }\n")
                .append("  .header-card { background: linear-gradient(135deg, #1e293b, #0f172a); border: 1px solid #334155; border-radius: 12px; padding: 24px; margin-bottom: 24px; box-shadow: 0 10px 15px -3px rgba(0,0,0,0.5); }\n")
                .append("  h1 { font-size: 26px; color: #38bdf8; margin: 0 0 8px 0; }\n")
                .append("  .subtitle { font-size: 14px; color: #94a3b8; margin: 0; }\n")
                .append("  .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }\n")
                .append("  .kpi-card { background-color: #1e293b; border: 1px solid #334155; border-radius: 10px; padding: 18px; text-align: center; }\n")
                .append("  .kpi-title { font-size: 12px; text-transform: uppercase; letter-spacing: 0.05em; color: #94a3b8; margin-bottom: 6px; }\n")
                .append("  .kpi-value { font-size: 28px; font-weight: bold; }\n")
                .append("  .status-PASS { color: #4ade80; }\n")
                .append("  .status-WARNING { color: #fbbf24; }\n")
                .append("  .status-FAIL { color: #f87171; }\n")
                .append("  .status-UNKNOWN { color: #94a3b8; }\n")
                .append("  .status-CONFLICT { color: #c084fc; }\n")
                .append("  .section-card { background-color: #1e293b; border: 1px solid #334155; border-radius: 12px; padding: 24px; margin-bottom: 24px; }\n")
                .append("  h2 { font-size: 18px; color: #f8fafc; margin-top: 0; border-bottom: 1px solid #334155; padding-bottom: 10px; }\n")
                .append("  table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 14px; }\n")
                .append("  th, td { padding: 10px 14px; text-align: left; border-bottom: 1px solid #334155; }\n")
                .append("  th { background-color: #0f172a; color: #94a3b8; font-weight: 600; }\n")
                .append("  tr:hover { background-color: #334155; }\n")
                .append("  .badge { display: inline-block; padding: 4px 10px; border-radius: 9999px; font-size: 12px; font-weight: 600; text-transform: uppercase; }\n")
                .append("  .badge-pass { background-color: rgba(74, 222, 128, 0.15); color: #4ade80; border: 1px solid #4ade80; }\n")
                .append("  .badge-warning { background-color: rgba(251, 191, 36, 0.15); color: #fbbf24; border: 1px solid #fbbf24; }\n")
                .append("  .badge-error { background-color: rgba(248, 113, 113, 0.15); color: #f87171; border: 1px solid #f87171; }\n")
                .append("  .badge-info { background-color: rgba(56, 189, 248, 0.15); color: #38bdf8; border: 1px solid #38bdf8; }\n")
                .append("</style>\n")
                .append("</head>\n")
                .append("<body>\n");

            // Header
            html.append("<div class=\"header-card\">\n")
                .append("  <h1>OME-Zarr Specification Compliance Report</h1>\n")
                .append("  <p class=\"subtitle\">Dataset: <strong>").append(escapeHtml(datasetName)).append("</strong> | Path: ").append(escapeHtml(zarrRoot.toAbsolutePath().toString())).append("</p>\n")
                .append("  <p class=\"subtitle\">Generated on ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n")
                .append("</div>\n");

            // KPI Grid
            html.append("<div class=\"kpi-grid\">\n")
                .append("  <div class=\"kpi-card\">\n")
                .append("    <div class=\"kpi-title\">Overall Status</div>\n")
                .append("    <div class=\"kpi-value status-").append(overallStatus.name()).append("\">").append(overallStatus.getDisplayName()).append("</div>\n")
                .append("  </div>\n")
                .append("  <div class=\"kpi-card\">\n")
                .append("    <div class=\"kpi-title\">Detected OME Version</div>\n")
                .append("    <div class=\"kpi-value\" style=\"color: #38bdf8;\">").append(escapeHtml(detectedOmeVer)).append("</div>\n")
                .append("  </div>\n")
                .append("  <div class=\"kpi-card\">\n")
                .append("    <div class=\"kpi-title\">Detected Zarr Format</div>\n")
                .append("    <div class=\"kpi-value\" style=\"color: #c084fc;\">Version ").append(escapeHtml(detectedZarrVer)).append("</div>\n")
                .append("  </div>\n")
                .append("  <div class=\"kpi-card\">\n")
                .append("    <div class=\"kpi-title\">Errors</div>\n")
                .append("    <div class=\"kpi-value\" style=\"color: #f87171;\">").append(errCount).append("</div>\n")
                .append("  </div>\n")
                .append("  <div class=\"kpi-card\">\n")
                .append("    <div class=\"kpi-title\">Warnings</div>\n")
                .append("    <div class=\"kpi-value\" style=\"color: #fbbf24;\">").append(warnCount).append("</div>\n")
                .append("  </div>\n")
                .append("  <div class=\"kpi-card\">\n")
                .append("    <div class=\"kpi-title\">Passed Checks</div>\n")
                .append("    <div class=\"kpi-value\" style=\"color: #4ade80;\">").append(passCount).append("</div>\n")
                .append("  </div>\n")
                .append("</div>\n");

            // Category Status Breakdown Card
            html.append("<div class=\"section-card\">\n")
                .append("  <h2>Validation Category Breakdown</h2>\n")
                .append("  <table>\n")
                .append("    <thead>\n")
                .append("      <tr><th>Category</th><th>Description</th><th>Status</th></tr>\n")
                .append("    </thead>\n")
                .append("    <tbody>\n");

            for (ComplianceCategory cat : ComplianceCategory.values()) {
                ComplianceSeverity sev = categoryStatuses.getOrDefault(cat, ComplianceSeverity.INFO);
                String badgeClass = switch (sev) {
                    case PASS -> "badge-pass";
                    case WARNING -> "badge-warning";
                    case ERROR -> "badge-error";
                    case INFO -> "badge-info";
                };
                html.append("      <tr>\n")
                    .append("        <td><strong>").append(cat.getDisplayName()).append("</strong></td>\n")
                    .append("        <td>").append(cat.getDescription()).append("</td>\n")
                    .append("        <td><span class=\"badge ").append(badgeClass).append("\">").append(sev.name()).append("</span></td>\n")
                    .append("      </tr>\n");
            }
            html.append("    </tbody>\n  </table>\n</div>\n");

            // Detailed Specification Checks Table
            html.append("<div class=\"section-card\">\n")
                .append("  <h2>Detailed Specification Checks (").append(issues.size()).append(" Total Checks Evaluated)</h2>\n")
                .append("  <table>\n")
                .append("    <thead>\n")
                .append("      <tr><th>Category</th><th>Severity</th><th>Specification Rule</th><th>Finding / Details</th><th>Expected</th><th>Actual</th></tr>\n")
                .append("    </thead>\n")
                .append("    <tbody>\n");

            for (ComplianceIssue issue : issues) {
                String badgeClass = switch (issue.severity()) {
                    case PASS -> "badge-pass";
                    case WARNING -> "badge-warning";
                    case ERROR -> "badge-error";
                    case INFO -> "badge-info";
                };
                html.append("      <tr>\n")
                    .append("        <td>").append(issue.category().getDisplayName()).append("</td>\n")
                    .append("        <td><span class=\"badge ").append(badgeClass).append("\">").append(issue.severity().name()).append("</span></td>\n")
                    .append("        <td>").append(escapeHtml(issue.rule())).append("</td>\n")
                    .append("        <td>").append(escapeHtml(issue.problem())).append("</td>\n")
                    .append("        <td>").append(escapeHtml(issue.expected())).append("</td>\n")
                    .append("        <td>").append(escapeHtml(issue.actual())).append("</td>\n")
                    .append("      </tr>\n");
            }

            html.append("    </tbody>\n  </table>\n</div>\n")
                .append("</body>\n</html>");

            Files.writeString(reportFile, html.toString());
            log.info("Generated HTML OME-Zarr compliance report at {}", reportFile.toAbsolutePath());
            return reportFile;

        } catch (Exception e) {
            log.error("Failed to generate HTML compliance report for {}", zarrRoot, e);
            return reportFile;
        }
    }

    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
}
