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

public class ZarrV2Validator {
    private static final ObjectMapper mapper = new ObjectMapper();

    public List<ComplianceIssue> validateZarrV2Structure(Path zarrRoot, List<String> datasetRelativePaths) {
        List<ComplianceIssue> issues = new ArrayList<>();
        ComplianceCategory cat = ComplianceCategory.STRUCTURE;

        // 1. Validate .zgroup
        File zgroupFile = zarrRoot.resolve(".zgroup").toFile();
        if (!zgroupFile.exists()) {
            issues.add(ComplianceIssue.error(cat, "Zarr v2 Core Spec", ".zgroup file missing at root of Zarr v2 container.", "Root .zgroup file", "Missing"));
        } else {
            try {
                JsonNode zgNode = mapper.readTree(zgroupFile);
                int format = zgNode.path("zarr_format").asInt(0);
                if (format != 2) {
                    issues.add(ComplianceIssue.error(cat, "Zarr v2 Core Spec", ".zgroup zarr_format is not 2.", "zarr_format = 2", String.valueOf(format)));
                } else {
                    issues.add(ComplianceIssue.pass(cat, "Zarr v2 Core Spec", ".zgroup contains valid zarr_format = 2"));
                }
            } catch (Exception e) {
                issues.add(ComplianceIssue.error(cat, "Zarr v2 Core Spec", ".zgroup file is invalid JSON: " + e.getMessage(), "Valid JSON", "JSON Syntax Error"));
            }
        }

        // 2. Validate root .zattrs
        File zattrsFile = zarrRoot.resolve(".zattrs").toFile();
        if (!zattrsFile.exists()) {
            issues.add(ComplianceIssue.error(cat, "Zarr v2 Core Spec", ".zattrs file missing at root.", "Root .zattrs file", "Missing"));
        } else {
            try {
                mapper.readTree(zattrsFile);
                issues.add(ComplianceIssue.pass(cat, "Zarr v2 Core Spec", "Root .zattrs is valid JSON"));
            } catch (Exception e) {
                issues.add(ComplianceIssue.error(cat, "Zarr v2 Core Spec", "Root .zattrs file is invalid JSON: " + e.getMessage(), "Valid JSON", "JSON Syntax Error"));
            }
        }

        // 3. Validate pyramid level arrays
        if (datasetRelativePaths != null && !datasetRelativePaths.isEmpty()) {
            for (String relPath : datasetRelativePaths) {
                Path levelDir = zarrRoot.resolve(relPath);
                if (!Files.exists(levelDir) || !Files.isDirectory(levelDir)) {
                    issues.add(ComplianceIssue.error(cat, "Zarr v2 Array Spec", "Referenced dataset directory does not exist: " + relPath, "Existing Directory", "Missing: " + relPath));
                    continue;
                }

                File zarrayFile = levelDir.resolve(".zarray").toFile();
                if (!zarrayFile.exists()) {
                    issues.add(ComplianceIssue.error(cat, "Zarr v2 Array Spec", ".zarray missing in pyramid dataset level: " + relPath, "File .zarray present", "Missing in " + relPath));
                    continue;
                }

                try {
                    JsonNode za = mapper.readTree(zarrayFile);
                    int zfmt = za.path("zarr_format").asInt(0);
                    if (zfmt != 2) {
                        issues.add(ComplianceIssue.error(cat, "Zarr v2 Array Spec", ".zarray zarr_format is not 2 in level " + relPath, "zarr_format = 2", String.valueOf(zfmt)));
                    }

                    JsonNode shapeNode = za.path("shape");
                    if (!shapeNode.isArray() || shapeNode.isEmpty()) {
                        issues.add(ComplianceIssue.error(cat, "Zarr v2 Array Spec", ".zarray shape is missing or empty in level " + relPath, "Non-empty array shape", shapeNode.toString()));
                    }

                    JsonNode chunksNode = za.path("chunks");
                    if (!chunksNode.isArray() || chunksNode.isEmpty()) {
                        issues.add(ComplianceIssue.error(cat, "Zarr v2 Array Spec", ".zarray chunks is missing or empty in level " + relPath, "Non-empty chunks array", chunksNode.toString()));
                    } else if (shapeNode.isArray() && chunksNode.size() != shapeNode.size()) {
                        issues.add(ComplianceIssue.error(cat, "Zarr v2 Array Spec", ".zarray chunks rank does not match shape rank in level " + relPath, "chunks rank = " + shapeNode.size(), "chunks rank = " + chunksNode.size()));
                    }

                    String dtype = za.path("dtype").asText("");
                    if (dtype.isBlank()) {
                        issues.add(ComplianceIssue.error(cat, "Zarr v2 Array Spec", ".zarray dtype is missing or blank in level " + relPath, "Valid dtype string", "Blank/Missing"));
                    }

                    issues.add(ComplianceIssue.pass(cat, "Zarr v2 Array Spec", "Pyramid level " + relPath + " contains valid .zarray metadata"));

                } catch (Exception e) {
                    issues.add(ComplianceIssue.error(cat, "Zarr v2 Array Spec", "Invalid JSON in .zarray for level " + relPath + ": " + e.getMessage(), "Valid JSON", "JSON Error"));
                }
            }
        }

        return issues;
    }
}
