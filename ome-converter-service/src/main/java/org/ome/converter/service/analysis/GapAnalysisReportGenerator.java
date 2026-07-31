package org.ome.converter.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.ome.converter.core.model.GapAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GapAnalysisReportGenerator {
    private static final Logger log = LoggerFactory.getLogger(GapAnalysisReportGenerator.class);
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void generateAllReports(GapAnalysisResult result, Path outputDirectory) {
        generateHtmlReport(result, outputDirectory);
        generateCsvReport(result, outputDirectory);
        generateJsonReport(result, outputDirectory);
    }

    public Path generateHtmlReport(GapAnalysisResult result, Path outputDirectory) {
        Path reportPath = outputDirectory.resolve("metadata_gap_report.html");
        File reportFile = reportPath.toFile();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(reportFile, StandardCharsets.UTF_8))) {
            bw.write(buildHtmlContent(result));
            log.info("Generated HTML Metadata Gap Analysis Report: {}", reportPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to generate HTML Gap Analysis Report at {}: {}", reportPath, e.getMessage(), e);
        }

        return reportPath;
    }

    public Path generateCsvReport(GapAnalysisResult result, Path outputDirectory) {
        Path reportPath = outputDirectory.resolve("metadata_gap_report.csv");
        File reportFile = reportPath.toFile();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(reportFile, StandardCharsets.UTF_8))) {
            bw.write("Original Key,Original Value,Status,Explanation\n");
            for (var item : result.lostItems()) {
                bw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    cleanCsv(item.originalKey()),
                    cleanCsv(item.originalValue()),
                    cleanCsv(item.status()),
                    cleanCsv(item.explanation())
                ));
            }
            log.info("Generated CSV Metadata Gap Analysis Report: {}", reportPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to generate CSV Gap Analysis Report at {}: {}", reportPath, e.getMessage(), e);
        }

        return reportPath;
    }

    public Path generateJsonReport(GapAnalysisResult result, Path outputDirectory) {
        Path reportPath = outputDirectory.resolve("metadata_gap_report.json");
        File reportFile = reportPath.toFile();

        try {
            Map<String, Object> jsonReport = new LinkedHashMap<>();
            jsonReport.put("datasetName", result.datasetName());
            jsonReport.put("targetVersion", result.targetVersion().getDisplayName());
            jsonReport.put("generatedTimestamp", LocalDateTime.now().toString());
            jsonReport.put("totalFields", result.totalOriginalCount());
            jsonReport.put("mappedFields", result.mappedCount());
            jsonReport.put("vendorDumpedFields", result.vendorDumpedCount());
            jsonReport.put("lossFields", result.lossCount());

            List<Map<String, String>> lostList = new ArrayList<>();
            for (var item : result.lostItems()) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("originalKey", item.originalKey());
                m.put("originalValue", item.originalValue());
                m.put("status", item.status());
                m.put("explanation", item.explanation());
                lostList.add(m);
            }
            jsonReport.put("lostMetadataInventory", lostList);

            objectMapper.writeValue(reportFile, jsonReport);
            log.info("Generated JSON Metadata Gap Analysis Report: {}", reportPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to generate JSON Gap Analysis Report at {}: {}", reportPath, e.getMessage(), e);
        }

        return reportPath;
    }

    private String cleanCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }

    private String buildHtmlContent(GapAnalysisResult r) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n")
          .append("<html lang=\"en\">\n")
          .append("<head>\n")
          .append("  <meta charset=\"UTF-8\">\n")
          .append("  <title>Metadata Gap Analysis Dashboard - ").append(escape(r.datasetName())).append("</title>\n")
          .append("  <style>\n")
          .append("    :root { --bg: #0b1120; --card-bg: #1e293b; --text: #f8fafc; --text-sub: #94a3b8; --border: #334155; --green: #22c55e; --purple: #a855f7; --red: #ef4444; }\n")
          .append("    body { background-color: var(--bg); color: var(--text); padding: 2rem; font-family: 'Segoe UI', sans-serif; }\n")
          .append("    .container { max-width: 1200px; margin: 0 auto; }\n")
          .append("    .header { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 1.5rem; margin-bottom: 1.5rem; }\n")
          .append("    .header h1 { margin: 0 0 0.5rem 0; font-size: 1.6rem; color: #38bdf8; }\n")
          .append("    .grid-kpi { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; margin-bottom: 1.5rem; }\n")
          .append("    .kpi-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; padding: 1.25rem; text-align: center; }\n")
          .append("    .kpi-title { font-size: 0.75rem; text-transform: uppercase; color: var(--text-sub); font-weight: 700; letter-spacing: 0.5px; }\n")
          .append("    .kpi-value { font-size: 2.2rem; font-weight: 800; margin: 0.4rem 0; color: var(--text); }\n")
          .append("    .breakdown-box { background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; padding: 1.25rem; margin-bottom: 1.5rem; }\n")
          .append("    .breakdown-title { font-size: 0.9rem; font-weight: 700; margin-bottom: 1rem; color: var(--text-sub); }\n")
          .append("    .badge-bar { display: flex; gap: 1rem; }\n")
          .append("    .badge { padding: 0.5rem 1rem; border-radius: 6px; font-weight: 700; font-size: 0.85rem; }\n")
          .append("    .badge-mapped { background: rgba(34, 197, 94, 0.15); color: #4ade80; border: 1px solid var(--green); }\n")
          .append("    .badge-vendor { background: rgba(168, 85, 247, 0.15); color: #c084fc; border: 1px solid var(--purple); }\n")
          .append("    .badge-loss { background: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid var(--red); }\n")
          .append("    table { width: 100%; border-collapse: collapse; font-size: 0.85rem; background: var(--card-bg); border-radius: 8px; }\n")
          .append("    th { background: #0f172a; color: var(--text-sub); padding: 0.75rem 1rem; border-bottom: 1px solid var(--border); font-weight: 700; text-transform: uppercase; font-size: 0.75rem; text-align: left; }\n")
          .append("    td { padding: 0.75rem 1rem; border-bottom: 1px solid var(--border); vertical-align: top; }\n")
          .append("    .tag-loss { background: rgba(239, 68, 68, 0.2); color: #f87171; padding: 0.2rem 0.5rem; border-radius: 4px; font-weight: 700; font-size: 0.75rem; border: 1px solid var(--red); }\n")
          .append("  </style>\n")
          .append("</head>\n")
          .append("<body>\n")
          .append("  <div class=\"container\">\n")
          .append("    <div class=\"header\">\n")
          .append("      <h1>Metadata Gap Analysis Dashboard</h1>\n")
          .append("      <p>Dataset: <strong>").append(escape(r.datasetName())).append("</strong> | Spec: <strong>").append(r.targetVersion().getDisplayName()).append("</strong> | Generated: ").append(timestamp).append("</p>\n")
          .append("    </div>\n")
          .append("    <div class=\"grid-kpi\">\n")
          .append("      <div class=\"kpi-card\"><div class=\"kpi-title\">Total Fields</div><div class=\"kpi-value\">").append(r.totalOriginalCount()).append("</div></div>\n")
          .append("      <div class=\"kpi-card\"><div class=\"kpi-title\">Mapped Fields</div><div class=\"kpi-value\" style=\"color: var(--green);\">").append(r.mappedCount()).append("</div></div>\n")
          .append("      <div class=\"kpi-card\"><div class=\"kpi-title\">Vendor Dumped</div><div class=\"kpi-value\" style=\"color: var(--purple);\">").append(r.vendorDumpedCount()).append("</div></div>\n")
          .append("      <div class=\"kpi-card\"><div class=\"kpi-title\">Loss</div><div class=\"kpi-value\" style=\"color: var(--red);\">").append(r.lossCount()).append("<span style=\"font-size:0.9rem; font-weight:600;\"> (Attention)</span></div></div>\n")
          .append("    </div>\n")
          .append("    <div class=\"breakdown-box\">\n")
          .append("      <div class=\"breakdown-title\">Metadata Classification Breakdown</div>\n")
          .append("      <div class=\"badge-bar\">\n")
          .append("        <span class=\"badge badge-mapped\">Mapped: ").append(r.mappedCount()).append("</span>\n")
          .append("        <span class=\"badge badge-vendor\">Vendor Custom (Dumped): ").append(r.vendorDumpedCount()).append("</span>\n")
          .append("        <span class=\"badge badge-loss\">Loss: ").append(r.lossCount()).append("</span>\n")
          .append("      </div>\n")
          .append("    </div>\n")
          .append("    <h3>Lost Metadata Inventory (Displaying ").append(r.lostItems().size()).append(" Lost / Missing Fields)</h3>\n")
          .append("    <table>\n")
          .append("      <thead><tr><th>Original Key</th><th>Original Value</th><th>Status</th><th>Explanation</th></tr></thead>\n")
          .append("      <tbody>\n");

        for (var item : r.lostItems()) {
            sb.append("        <tr>\n")
              .append("          <td><strong>").append(escape(item.originalKey())).append("</strong></td>\n")
              .append("          <td>").append(escape(item.originalValue())).append("</td>\n")
              .append("          <td><span class=\"tag-loss\">").append(escape(item.status())).append("</span></td>\n")
              .append("          <td>").append(escape(item.explanation())).append("</td>\n")
              .append("        </tr>\n");
        }

        sb.append("      </tbody>\n")
          .append("    </table>\n")
          .append("  </div>\n")
          .append("</body>\n")
          .append("</html>\n");

        return sb.toString();
    }

    private String escape(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
