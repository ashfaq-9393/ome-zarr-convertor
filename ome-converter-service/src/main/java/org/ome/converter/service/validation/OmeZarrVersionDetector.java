package org.ome.converter.service.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class OmeZarrVersionDetector {
    private static final Logger log = LoggerFactory.getLogger(OmeZarrVersionDetector.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public record DetectionResult(
        String omeZarrVersion,   // "0.4", "0.5", "UNKNOWN", "VERSION_CONFLICT"
        String zarrFormatVersion, // "2", "3", "UNKNOWN"
        String details
    ) {}

    public DetectionResult detectVersion(Path zarrRoot) {
        if (zarrRoot == null || !Files.exists(zarrRoot) || !Files.isDirectory(zarrRoot)) {
            return new DetectionResult("UNKNOWN", "UNKNOWN", "Dataset path does not exist or is not a directory.");
        }

        File zarrJson = zarrRoot.resolve("zarr.json").toFile();
        File zgroup = zarrRoot.resolve(".zgroup").toFile();
        File zattrs = zarrRoot.resolve(".zattrs").toFile();

        String zarrVersion = "UNKNOWN";
        Set<String> detectedOmeVersions = new HashSet<>();
        StringBuilder details = new StringBuilder();

        // 1. Inspect Zarr v3 (zarr.json)
        if (zarrJson.exists() && zarrJson.isFile()) {
            zarrVersion = "3";
            try {
                JsonNode root = mapper.readTree(zarrJson);
                int format = root.path("zarr_format").asInt(0);
                if (format == 3) {
                    details.append("Found zarr.json with zarr_format 3. ");
                } else {
                    details.append("zarr.json has zarr_format: ").append(format).append(". ");
                }

                JsonNode attributes = root.path("attributes");
                if (attributes.isObject()) {
                    // Check attributes.ome.version
                    JsonNode omeNode = attributes.path("ome");
                    if (omeNode.isObject() && omeNode.has("version")) {
                        String vStr = omeNode.path("version").asText();
                        detectedOmeVersions.add(cleanVersionString(vStr));
                        details.append("Found attributes.ome.version: '").append(vStr).append("'. ");
                    }

                    // Check multiscales inside attributes.ome or root attributes
                    JsonNode multiscales = omeNode.isObject() ? omeNode.path("multiscales") : attributes.path("multiscales");
                    collectMultiscalesVersions(multiscales, detectedOmeVersions, details);
                }
            } catch (Exception e) {
                log.warn("Error parsing zarr.json in {}: {}", zarrRoot, e.getMessage());
                details.append("Error parsing zarr.json: ").append(e.getMessage()).append(". ");
            }
        }

        // 2. Inspect Zarr v2 (.zgroup / .zattrs)
        if (zgroup.exists() && zgroup.isFile()) {
            if ("UNKNOWN".equals(zarrVersion)) {
                zarrVersion = "2";
            }
            details.append("Found .zgroup file. ");
        }

        if (zattrs.exists() && zattrs.isFile()) {
            if ("UNKNOWN".equals(zarrVersion)) {
                zarrVersion = "2";
            }
            try {
                JsonNode root = mapper.readTree(zattrs);
                // Check multiscales in .zattrs
                JsonNode multiscales = root.path("multiscales");
                collectMultiscalesVersions(multiscales, detectedOmeVersions, details);

                // Check ome object in .zattrs if present
                JsonNode omeNode = root.path("ome");
                if (omeNode.isObject() && omeNode.has("version")) {
                    String vStr = omeNode.path("version").asText();
                    detectedOmeVersions.add(cleanVersionString(vStr));
                    details.append("Found .zattrs ome.version: '").append(vStr).append("'. ");
                }
            } catch (Exception e) {
                log.warn("Error parsing .zattrs in {}: {}", zarrRoot, e.getMessage());
                details.append("Error parsing .zattrs: ").append(e.getMessage()).append(". ");
            }
        }

        // 3. Resolve detected OME-Zarr version
        if (detectedOmeVersions.size() > 1) {
            log.warn("Conflicting OME-Zarr versions detected in {}: {}", zarrRoot, detectedOmeVersions);
            return new DetectionResult("VERSION_CONFLICT", zarrVersion, "Conflicting version tags found: " + detectedOmeVersions + ". Details: " + details);
        } else if (detectedOmeVersions.size() == 1) {
            String omeVersion = detectedOmeVersions.iterator().next();
            return new DetectionResult(omeVersion, zarrVersion, details.toString().trim());
        }

        // 4. Fallback heuristics if explicit version tag was not in multiscales/ome
        // Check bioformats2raw layout
        File b2rLayout = zarrRoot.resolve("bioformats2raw.layout").toFile();
        if (b2rLayout.exists()) {
            details.append("Found bioformats2raw.layout. ");
        }

        return new DetectionResult("UNKNOWN", zarrVersion, "Could not identify explicit OME-NGFF version metadata. Details: " + details.toString().trim());
    }

    private void collectMultiscalesVersions(JsonNode multiscales, Set<String> detectedOmeVersions, StringBuilder details) {
        if (multiscales.isArray() && !multiscales.isEmpty()) {
            for (JsonNode ms : multiscales) {
                if (ms.has("version")) {
                    String version = cleanVersionString(ms.path("version").asText());
                    if (!version.isBlank()) {
                        detectedOmeVersions.add(version);
                        details.append("Found multiscales version: '").append(version).append("'. ");
                    }
                }
            }
        }
    }

    private String cleanVersionString(String ver) {
        if (ver == null) return "";
        String v = ver.trim();
        if (v.startsWith("0.4")) return "0.4";
        if (v.startsWith("0.5")) return "0.5";
        return v;
    }
}
