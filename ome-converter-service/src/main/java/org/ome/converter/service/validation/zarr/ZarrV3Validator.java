package org.ome.converter.service.validation.zarr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ome.converter.service.validation.ComplianceCategory;
import org.ome.converter.service.validation.ComplianceIssue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ZarrV3Validator {
    private static final ObjectMapper mapper = new ObjectMapper();

    public List<ComplianceIssue> validateZarrV3Structure(Path zarrRoot, List<String> datasetRelativePaths) {
        List<ComplianceIssue> issues = new ArrayList<>();
        ComplianceCategory cat = ComplianceCategory.STRUCTURE;

        // 1. Validate root zarr.json
        File zarrJson = zarrRoot.resolve("zarr.json").toFile();
        if (!zarrJson.exists()) {
            issues.add(ComplianceIssue.error(cat, "Zarr v3 Core Spec", "zarr.json file missing at root of Zarr v3 container.", "Root zarr.json file", "Missing"));
        } else {
            try {
                JsonNode root = mapper.readTree(zarrJson);
                int format = root.path("zarr_format").asInt(0);
                if (format != 3) {
                    issues.add(ComplianceIssue.error(cat, "Zarr v3 Core Spec", "zarr.json zarr_format is not 3.", "zarr_format = 3", String.valueOf(format)));
                }

                String nodeType = root.path("node_type").asText("");
                if (!"group".equalsIgnoreCase(nodeType)) {
                    issues.add(ComplianceIssue.error(cat, "Zarr v3 Core Spec", "Root zarr.json node_type must be 'group'.", "node_type = 'group'", nodeType));
                } else {
                    issues.add(ComplianceIssue.pass(cat, "Zarr v3 Core Spec", "Root zarr.json contains valid zarr_format = 3 and node_type = 'group'"));
                }

                JsonNode attributes = root.path("attributes");
                if (!attributes.isObject()) {
                    issues.add(ComplianceIssue.error(cat, "Zarr v3 Core Spec", "Root zarr.json missing 'attributes' object.", "Object 'attributes'", attributes.getNodeType().toString()));
                }

            } catch (Exception e) {
                issues.add(ComplianceIssue.error(cat, "Zarr v3 Core Spec", "Root zarr.json file is invalid JSON: " + e.getMessage(), "Valid JSON", "JSON Syntax Error"));
            }
        }

        // 2. Validate pyramid level arrays
        if (datasetRelativePaths != null && !datasetRelativePaths.isEmpty()) {
            for (String relPath : datasetRelativePaths) {
                Path levelDir = zarrRoot.resolve(relPath);
                if (!Files.exists(levelDir) || !Files.isDirectory(levelDir)) {
                    issues.add(ComplianceIssue.error(cat, "Zarr v3 Array Spec", "Referenced dataset directory does not exist: " + relPath, "Existing Directory", "Missing: " + relPath));
                    continue;
                }

                File arrayJson = levelDir.resolve("zarr.json").toFile();
                if (!arrayJson.exists()) {
                    issues.add(ComplianceIssue.error(cat, "Zarr v3 Array Spec", "zarr.json missing in pyramid dataset level: " + relPath, "File zarr.json present", "Missing in " + relPath));
                    continue;
                }

                try {
                    JsonNode arrayNode = mapper.readTree(arrayJson);
                    int zfmt = arrayNode.path("zarr_format").asInt(0);
                    if (zfmt != 3) {
                        issues.add(ComplianceIssue.error(cat, "Zarr v3 Array Spec", "zarr.json zarr_format is not 3 in level " + relPath, "zarr_format = 3", String.valueOf(zfmt)));
                    }

                    String nodeType = arrayNode.path("node_type").asText("");
                    if (!"array".equalsIgnoreCase(nodeType)) {
                        issues.add(ComplianceIssue.error(cat, "Zarr v3 Array Spec", "Pyramid level zarr.json node_type must be 'array' in level " + relPath, "node_type = 'array'", nodeType));
                    }

                    JsonNode shapeNode = arrayNode.path("shape");
                    if (!shapeNode.isArray() || shapeNode.isEmpty()) {
                        issues.add(ComplianceIssue.error(cat, "Zarr v3 Array Spec", "zarr.json shape is missing or empty in level " + relPath, "Non-empty array shape", shapeNode.toString()));
                    }

                    String dataType = arrayNode.path("data_type").asText("");
                    if (dataType.isBlank()) {
                        issues.add(ComplianceIssue.error(cat, "Zarr v3 Array Spec", "zarr.json data_type is missing or blank in level " + relPath, "Valid data_type string", "Blank/Missing"));
                    }

                    JsonNode chunkGrid = arrayNode.path("chunk_grid");
                    if (!chunkGrid.isObject() || !chunkGrid.has("name")) {
                        issues.add(ComplianceIssue.error(cat, "Zarr v3 Array Spec", "zarr.json chunk_grid configuration missing in level " + relPath, "Valid chunk_grid object", chunkGrid.toString()));
                    }

                    JsonNode codecs = arrayNode.path("codecs");
                    if (!codecs.isArray() || codecs.isEmpty()) {
                        issues.add(ComplianceIssue.error(cat, "Zarr v3 Array Spec", "zarr.json codecs array is missing or empty in level " + relPath, "Non-empty codecs array", codecs.toString()));
                    }

                    issues.add(ComplianceIssue.pass(cat, "Zarr v3 Array Spec", "Pyramid level " + relPath + " contains valid Zarr v3 array metadata"));

                } catch (Exception e) {
                    issues.add(ComplianceIssue.error(cat, "Zarr v3 Array Spec", "Invalid JSON in level zarr.json for " + relPath + ": " + e.getMessage(), "Valid JSON", "JSON Error"));
                }
            }
        }

        return issues;
    }
}
