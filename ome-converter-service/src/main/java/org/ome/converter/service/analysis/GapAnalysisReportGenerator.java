package org.ome.converter.service.analysis;

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
import java.util.List;

public class GapAnalysisReportGenerator {
    private static final Logger log = LoggerFactory.getLogger(GapAnalysisReportGenerator.class);

    public void generateAllReports(GapAnalysisResult result, Path outputDirectory) {
        generateHtmlReport(result, outputDirectory);
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



    public Path generateHtmlReportToFile(GapAnalysisResult result, File destinationFile) {
        if (destinationFile == null) return null;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(destinationFile, StandardCharsets.UTF_8))) {
            bw.write(buildHtmlContent(result));
            log.info("Successfully exported HTML Gap Analysis Report to: {}", destinationFile.getAbsolutePath());
            return destinationFile.toPath();
        } catch (Exception e) {
            log.error("Failed to export HTML Gap Analysis Report to {}: {}", destinationFile.getAbsolutePath(), e.getMessage(), e);
            return null;
        }
    }

    private String buildHtmlContent(GapAnalysisResult r) {
        List<GapAnalysisResult.GapAnalysisItemDetail> items =
            (r.allItems() != null && !r.allItems().isEmpty()) ? r.allItems()
                : (r.lostItems() != null ? r.lostItems() : List.of());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n")
          .append("<html lang=\"en\">\n")
          .append("<head>\n")
          .append("  <meta charset=\"UTF-8\">\n")
          .append("  <title>Metadata Gap Analysis Dashboard - ").append(escape(r.datasetName())).append("</title>\n")
          .append("  <style>\n")
          .append("    :root { --bg: #f3f6fa; --card-bg: #ffffff; --text: #172033; --text-sub: #5d6978; --border: #d7dfe9; --green: #16803c; --purple: #7446a8; --red: #c73535; --blue: #156f9f; }\n")
          .append("    body { background-color: var(--bg); color: var(--text); padding: 2rem; font-family: 'Segoe UI', sans-serif; }\n")
          .append("    .container { max-width: 1200px; margin: 0 auto; }\n")
          .append("    .header { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 1.5rem; margin-bottom: 1.5rem; }\n")
          .append("    .header h1 { margin: 0 0 0.5rem 0; font-size: 1.6rem; color: var(--blue); }\n")
          .append("    .grid-kpi { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; margin-bottom: 1.5rem; }\n")
          .append("    .kpi-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; padding: 1.25rem; text-align: center; }\n")
          .append("    .kpi-title { font-size: 0.75rem; text-transform: uppercase; color: var(--text-sub); font-weight: 700; letter-spacing: 0.5px; }\n")
          .append("    .kpi-value { font-size: 2.2rem; font-weight: 800; margin: 0.4rem 0; color: var(--text); }\n")
          .append("    .breakdown-box { background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; padding: 1.25rem; margin-bottom: 1.5rem; }\n")
          .append("    .breakdown-title { font-size: 0.9rem; font-weight: 700; margin-bottom: 1rem; color: var(--text-sub); }\n")
          .append("    .badge-bar { display: flex; gap: 1rem; }\n")
          .append("    .badge { padding: 0.5rem 1rem; border-radius: 6px; font-weight: 700; font-size: 0.85rem; }\n")
          .append("    .badge-mapped { background: #eaf7ef; color: var(--green); border: 1px solid #9fd5b2; }\n")
          .append("    .badge-vendor { background: #f3edf9; color: var(--purple); border: 1px solid #cbb6df; }\n")
          .append("    .badge-loss { background: #fceeee; color: var(--red); border: 1px solid #e5aaaa; }\n")
          .append("    .inventory-panel { background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; padding: 1.25rem; overflow: hidden; }\n")
          .append("    .inventory-toolbar { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 1rem; }\n")
          .append("    .inventory-toolbar h3 { margin: 0 1rem 0 0; }\n")
          .append("    .filter-btn { cursor: pointer; color: var(--text-sub); background: #f8fafc; border: 1px solid var(--border); border-radius: 6px; padding: 0.5rem 0.8rem; font-weight: 700; }\n")
          .append("    .filter-btn:hover, .filter-btn:focus-visible { color: var(--text); border-color: var(--blue); outline: none; }\n")
          .append("    .filter-btn.active[data-filter='MAPPED'] { color: var(--green); border-color: var(--green); background: #eaf7ef; }\n")
          .append("    .filter-btn.active[data-filter='VENDOR'] { color: var(--purple); border-color: var(--purple); background: #f3edf9; }\n")
          .append("    .filter-btn.active[data-filter='LOSS'] { color: var(--red); border-color: var(--red); background: #fceeee; }\n")
          .append("    .filter-btn.active[data-filter='ALL'] { color: var(--blue); border-color: var(--blue); background: #edf6fa; }\n")
          .append("    table { width: 100%; border-collapse: collapse; font-size: 0.85rem; background: var(--card-bg); border-radius: 8px; }\n")
          .append("    th { background: #edf2f7; color: var(--text-sub); padding: 0.75rem 1rem; border-bottom: 1px solid var(--border); font-weight: 700; text-transform: uppercase; font-size: 0.75rem; text-align: left; }\n")
          .append("    td { padding: 0.75rem 1rem; border-bottom: 1px solid var(--border); vertical-align: top; word-break: break-word; }\n")
          .append("    .loc-code { background: #edf6fa; color: #075d83; padding: 0.2rem 0.4rem; border-radius: 4px; font-family: monospace; font-size: 0.75rem; }\n")
          .append("    tr[hidden] { display: none; }\n")
          .append("    @media (max-width: 760px) { body { padding: 0.75rem; } .grid-kpi { grid-template-columns: repeat(2, 1fr); } .badge-bar { flex-wrap: wrap; } .inventory-panel { overflow-x: auto; } table { min-width: 900px; } }\n")
          .append("  </style>\n")
          .append("</head>\n")
          .append("<body>\n")
          .append("  <div class=\"container\">\n")
          .append("    <div class=\"header\">\n")
          .append("      <h1>Metadata Gap Analysis Dashboard</h1>\n")
          .append("      <p>Dataset: <strong>").append(escape(r.datasetName())).append("</strong> | Spec: <strong>").append(r.targetVersion().getDisplayName()).append("</strong> | Exported: ").append(timestamp).append("</p>\n")
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
          .append("    <div class=\"inventory-panel\">\n")
          .append("      <div class=\"inventory-toolbar\">\n")
          .append("        <h3 id=\"inventory-title\">Inventory</h3>\n")
          .append("        <button class=\"filter-btn active\" type=\"button\" data-filter=\"LOSS\">Loss (").append(r.lossCount()).append(")</button>\n")
          .append("        <button class=\"filter-btn\" type=\"button\" data-filter=\"MAPPED\">Mapped (").append(r.mappedCount()).append(")</button>\n")
          .append("        <button class=\"filter-btn\" type=\"button\" data-filter=\"VENDOR\">Vendor (").append(r.vendorDumpedCount()).append(")</button>\n")
          .append("        <button class=\"filter-btn\" type=\"button\" data-filter=\"ALL\">All (").append(r.totalOriginalCount()).append(")</button>\n")
          .append("      </div>\n")
          .append("    <table>\n")
          .append("      <thead><tr><th>Original Key</th><th>Original Value</th><th>Converted Location</th><th>Converted Value</th><th>Explanation</th></tr></thead>\n")
          .append("      <tbody>\n");

        for (var item : items) {
            sb.append("        <tr data-category=\"").append(categoryOf(item.status())).append("\">\n")
              .append("          <td><strong>").append(escape(item.originalKey())).append("</strong></td>\n")
              .append("          <td>").append(escape(item.originalValue())).append("</td>\n")
              .append("          <td><span class=\"loc-code\">").append(escape(item.convertedLocation())).append("</span></td>\n")
              .append("          <td>").append(escape(item.convertedValue())).append("</td>\n")
              .append("          <td>").append(escape(item.explanation())).append("</td>\n")
              .append("        </tr>\n");
        }

        sb.append("      </tbody>\n")
          .append("    </table>\n")
                    .append("    </div>\n")
          .append("  </div>\n")
                    .append("  <script>\n")
                    .append("    const buttons = document.querySelectorAll('.filter-btn');\n")
                    .append("    const rows = document.querySelectorAll('tbody tr[data-category]');\n")
                    .append("    const title = document.getElementById('inventory-title');\n")
                    .append("    function showCategory(category) {\n")
                    .append("      let visibleCount = 0;\n")
                    .append("      rows.forEach(row => { const visible = category === 'ALL' || row.dataset.category === category; row.hidden = !visible; if (visible) visibleCount++; });\n")
                    .append("      buttons.forEach(button => { const active = button.dataset.filter === category; button.classList.toggle('active', active); button.setAttribute('aria-pressed', String(active)); });\n")
                    .append("      const label = category.charAt(0) + category.slice(1).toLowerCase();\n")
                    .append("      title.textContent = `Inventory - ${label} (${visibleCount})`;\n")
                    .append("    }\n")
                    .append("    buttons.forEach(button => button.addEventListener('click', () => showCategory(button.dataset.filter)));\n")
                    .append("    showCategory('LOSS');\n")
                    .append("  </script>\n")
          .append("</body>\n")
          .append("</html>\n");

        return sb.toString();
    }

    private String categoryOf(String status) {
        String value = status != null ? status.toUpperCase() : "";
        if (value.contains("MAPPED") && !value.contains("UNMAPPED")) return "MAPPED";
        if (value.contains("VENDOR") || value.contains("STRUCTURAL")) return "VENDOR";
        if (value.contains("LOSS") || value.contains("MISSING") || value.contains("UNMAPPED") || value.contains("UNREGISTERED")) return "LOSS";
        return "OTHER";
    }

    private String escape(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
