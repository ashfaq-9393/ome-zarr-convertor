package org.ome.converter.service.validation.v04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ome.converter.service.validation.ComplianceCategory;
import org.ome.converter.service.validation.ComplianceIssue;
import org.ome.converter.service.validation.zarr.ZarrV2Validator;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class OmeZarrV04ComplianceValidator {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final ZarrV2Validator zarrV2Validator = new ZarrV2Validator();

    public List<ComplianceIssue> validate(Path zarrRoot) {
        List<ComplianceIssue> issues = new ArrayList<>();

        File zattrsFile = zarrRoot.resolve(".zattrs").toFile();
        if (!zattrsFile.exists()) {
            issues.add(ComplianceIssue.error(ComplianceCategory.STRUCTURE, "OME-NGFF 0.4 Spec", "Root .zattrs file missing.", "Root .zattrs file", "Missing"));
            return issues;
        }

        JsonNode rootAttrs;
        try {
            rootAttrs = mapper.readTree(zattrsFile);
        } catch (Exception e) {
            issues.add(ComplianceIssue.error(ComplianceCategory.STRUCTURE, "OME-NGFF 0.4 Spec", "Failed to parse root .zattrs JSON: " + e.getMessage(), "Valid JSON", "JSON Error"));
            return issues;
        }

        // Collect dataset paths for Zarr structure check
        List<String> datasetPaths = extractDatasetPaths(rootAttrs);

        // Level 1: Zarr v2 Structure Check
        issues.addAll(zarrV2Validator.validateZarrV2Structure(zarrRoot, datasetPaths));

        // Level 2: OME-NGFF Version
        validateVersion(rootAttrs, issues);

        // Level 3: Multiscales
        validateMultiscales(rootAttrs, zarrRoot, issues, datasetPaths);

        // Level 4: Axes
        validateAxes(rootAttrs, zarrRoot, issues, datasetPaths);

        // Level 5: Coordinate Transformations
        validateTransformations(rootAttrs, issues);

        // Level 6: OMERO Metadata
        validateOmero(rootAttrs, zarrRoot, issues, datasetPaths);

        // Level 7: Labels
        validateLabels(rootAttrs, zarrRoot, issues);

        // Level 8: Plate / Well
        validatePlateWell(rootAttrs, issues);

        // Level 9: Bioformats2raw & Companion OME-XML
        validateTransitional(zarrRoot, issues);

        return issues;
    }

    private List<String> extractDatasetPaths(JsonNode rootAttrs) {
        List<String> paths = new ArrayList<>();
        JsonNode multiscales = rootAttrs.path("multiscales");
        if (multiscales.isArray() && !multiscales.isEmpty()) {
            JsonNode ms0 = multiscales.get(0);
            JsonNode datasets = ms0.path("datasets");
            if (datasets.isArray()) {
                for (JsonNode ds : datasets) {
                    String p = ds.path("path").asText("");
                    if (!p.isBlank()) {
                        paths.add(p);
                    }
                }
            }
        }
        return paths;
    }

    private void validateVersion(JsonNode rootAttrs, List<ComplianceIssue> issues) {
        ComplianceCategory cat = ComplianceCategory.VERSION;
        JsonNode multiscales = rootAttrs.path("multiscales");
        if (multiscales.isArray() && !multiscales.isEmpty()) {
            boolean versionFound = false;
            for (JsonNode ms : multiscales) {
                String ver = ms.path("version").asText("");
                if ("0.4".equals(ver)) {
                    versionFound = true;
                } else if (!ver.isBlank()) {
                    issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §2", "multiscales version mismatch: expected '0.4'", "0.4", ver));
                }
            }
            if (versionFound) {
                issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.4 §2", "multiscales version is correctly set to '0.4'"));
            } else {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §2", "multiscales version field missing or invalid", "version = '0.4'", "Missing"));
            }
        } else {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §2", "multiscales attribute missing or empty in .zattrs", "Non-empty multiscales array", "Missing/Empty"));
        }
    }

    private void validateMultiscales(JsonNode rootAttrs, Path zarrRoot, List<ComplianceIssue> issues, List<String> datasetPaths) {
        ComplianceCategory cat = ComplianceCategory.MULTISCALES;
        JsonNode multiscales = rootAttrs.path("multiscales");
        if (!multiscales.isArray() || multiscales.isEmpty()) {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §3", "Root multiscales array missing or empty", "multiscales array", "Missing/Empty"));
            return;
        }

        JsonNode ms0 = multiscales.get(0);
        String name = ms0.path("name").asText("");
        if (name.isBlank()) {
            issues.add(ComplianceIssue.warning(cat, "OME-NGFF 0.4 §3", "multiscales[0].name is recommended", "Descriptive name string", "Blank/Missing"));
        } else {
            issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.4 §3", "multiscales[0].name is present ('" + name + "')"));
        }

        JsonNode datasets = ms0.path("datasets");
        if (!datasets.isArray() || datasets.isEmpty()) {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §3", "multiscales[0].datasets missing or empty", "Non-empty datasets list", "Missing/Empty"));
            return;
        }

        issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.4 §3", "multiscales[0].datasets contains " + datasets.size() + " pyramid levels"));

        for (String p : datasetPaths) {
            Path levelDir = zarrRoot.resolve(p);
            if (Files.exists(levelDir)) {
                issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.4 §3", "Dataset level path '" + p + "' exists on disk"));
            } else {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §3", "Dataset level path '" + p + "' does not exist on disk", "Existing directory", "Missing: " + p));
            }
        }
    }

    private void validateAxes(JsonNode rootAttrs, Path zarrRoot, List<ComplianceIssue> issues, List<String> datasetPaths) {
        ComplianceCategory cat = ComplianceCategory.AXES;
        JsonNode multiscales = rootAttrs.path("multiscales");
        if (!multiscales.isArray() || multiscales.isEmpty()) return;

        JsonNode ms0 = multiscales.get(0);
        JsonNode axesNode = ms0.path("axes");

        if (axesNode.isMissingNode() || axesNode.isNull()) {
            issues.add(ComplianceIssue.warning(cat, "OME-NGFF 0.4 §4", "axes metadata is missing in multiscales[0]", "axes array", "Missing"));
            return;
        }

        if (!axesNode.isArray() || axesNode.isEmpty()) {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §4", "axes must be a non-empty array", "Non-empty axes array", axesNode.toString()));
            return;
        }

        List<String> axisNames = new ArrayList<>();
        Set<String> uniqueNames = new HashSet<>();
        Set<String> allowedTypes = Set.of("space", "time", "channel");

        for (int i = 0; i < axesNode.size(); i++) {
            JsonNode ax = axesNode.get(i);
            String axName = "";
            String axType = "";

            if (ax.isTextual()) {
                axName = ax.asText();
            } else if (ax.isObject()) {
                axName = ax.path("name").asText("");
                axType = ax.path("type").asText("");
                if (!axType.isBlank() && !allowedTypes.contains(axType.toLowerCase())) {
                    issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §4", "Invalid axis type '" + axType + "' for axis '" + axName + "'", "space, time, or channel", axType));
                }
            }

            if (axName.isBlank()) {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §4", "Axis at index " + i + " missing name", "Non-blank name", "Blank"));
            } else {
                axisNames.add(axName);
                if (!uniqueNames.add(axName.toLowerCase())) {
                    issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §4", "Duplicate axis name detected: '" + axName + "'", "Unique axis names", "Duplicate: " + axName));
                }
            }
        }

        issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.4 §4", "axes metadata contains " + axisNames.size() + " unique axis definitions: " + axisNames));

        // Validate axis count against dataset array shape rank
        if (!datasetPaths.isEmpty()) {
            Path level0Zarray = zarrRoot.resolve(datasetPaths.get(0)).resolve(".zarray");
            if (level0Zarray.toFile().exists()) {
                try {
                    JsonNode za = mapper.readTree(level0Zarray.toFile());
                    JsonNode shapeNode = za.path("shape");
                    if (shapeNode.isArray() && shapeNode.size() != axesNode.size()) {
                        issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §4", "axes count (" + axesNode.size() + ") does not match array shape rank (" + shapeNode.size() + ")", "Equal rank (" + shapeNode.size() + ")", String.valueOf(axesNode.size())));
                    } else {
                        issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.4 §4", "axes count matches dataset array shape rank (" + shapeNode.size() + "D)"));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void validateTransformations(JsonNode rootAttrs, List<ComplianceIssue> issues) {
        ComplianceCategory cat = ComplianceCategory.TRANSFORMATIONS;
        JsonNode multiscales = rootAttrs.path("multiscales");
        if (!multiscales.isArray() || multiscales.isEmpty()) return;

        JsonNode ms0 = multiscales.get(0);
        JsonNode rootTransforms = ms0.path("coordinateTransformations");
        JsonNode datasets = ms0.path("datasets");

        boolean hasTransform = (rootTransforms.isArray() && !rootTransforms.isEmpty());

        if (datasets.isArray()) {
            for (int i = 0; i < datasets.size(); i++) {
                JsonNode ds = datasets.get(i);
                JsonNode dsTransforms = ds.path("coordinateTransformations");
                if (dsTransforms.isArray() && !dsTransforms.isEmpty()) {
                    hasTransform = true;
                    for (JsonNode t : dsTransforms) {
                        validateSingleTransform(t, "multiscales[0].datasets[" + i + "]", issues);
                    }
                }
            }
        }

        if (rootTransforms.isArray()) {
            for (JsonNode t : rootTransforms) {
                validateSingleTransform(t, "multiscales[0]", issues);
            }
        }

        if (!hasTransform) {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §5", "No coordinateTransformations found in multiscales or datasets", "Scale transformation array", "Missing"));
        } else {
            issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.4 §5", "Valid coordinateTransformations defined in multiscales"));
        }
    }

    private void validateSingleTransform(JsonNode t, String location, List<ComplianceIssue> issues) {
        ComplianceCategory cat = ComplianceCategory.TRANSFORMATIONS;
        String type = t.path("type").asText("");
        if (!Set.of("scale", "translation", "identity").contains(type.toLowerCase())) {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §5", "Invalid transformation type '" + type + "' in " + location, "scale, translation, or identity", type));
            return;
        }

        if ("scale".equalsIgnoreCase(type)) {
            JsonNode scaleNode = t.path("scale");
            if (!scaleNode.isArray() || scaleNode.isEmpty()) {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §5", "Scale transformation missing 'scale' numeric array in " + location, "Non-empty scale array", scaleNode.toString()));
            } else {
                for (JsonNode val : scaleNode) {
                    if (val.asDouble(0.0) <= 0) {
                        issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §5", "Scale transformation values must be positive non-zero numbers in " + location, "> 0", val.toString()));
                    }
                }
            }
        } else if ("translation".equalsIgnoreCase(type)) {
            JsonNode transNode = t.path("translation");
            if (!transNode.isArray() || transNode.isEmpty()) {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.4 §5", "Translation transformation missing 'translation' numeric array in " + location, "Non-empty translation array", transNode.toString()));
            }
        }
    }

    private void validateOmero(JsonNode rootAttrs, Path zarrRoot, List<ComplianceIssue> issues, List<String> datasetPaths) {
        ComplianceCategory cat = ComplianceCategory.OMERO;
        JsonNode omeroNode = rootAttrs.path("omero");
        if (omeroNode.isMissingNode() || omeroNode.isNull()) {
            issues.add(ComplianceIssue.info(cat, "OMERO Spec", "OMERO channel rendering metadata not present in .zattrs", "OMERO metadata", "Not Present"));
            return;
        }

        JsonNode channels = omeroNode.path("channels");
        if (!channels.isArray() || channels.isEmpty()) {
            issues.add(ComplianceIssue.warning(cat, "OMERO Spec", "omero.channels is missing or empty", "Non-empty channels array", channels.toString()));
            return;
        }

        issues.add(ComplianceIssue.pass(cat, "OMERO Spec", "omero.channels contains " + channels.size() + " channel definitions"));

        for (int i = 0; i < channels.size(); i++) {
            JsonNode ch = channels.get(i);
            String label = ch.path("label").asText("");
            String color = ch.path("color").asText("");
            if (label.isBlank()) {
                issues.add(ComplianceIssue.warning(cat, "OMERO Spec", "omero.channels[" + i + "] missing label", "Channel label string", "Blank"));
            }
            if (color.isBlank()) {
                issues.add(ComplianceIssue.warning(cat, "OMERO Spec", "omero.channels[" + i + "] missing hex color string", "Hex color string (e.g. FF0000)", "Blank"));
            }
        }

        // Validate channel count against array sizeC
        if (!datasetPaths.isEmpty()) {
            Path level0Zarray = zarrRoot.resolve(datasetPaths.get(0)).resolve(".zarray");
            if (level0Zarray.toFile().exists()) {
                try {
                    JsonNode za = mapper.readTree(level0Zarray.toFile());
                    JsonNode shape = za.path("shape");
                    if (shape.isArray() && shape.size() >= 2) {
                        int sizeC = shape.get(1).asInt(1);
                        if (sizeC != channels.size()) {
                            issues.add(ComplianceIssue.warning(cat, "OMERO Spec", "omero.channels count (" + channels.size() + ") does not match array sizeC (" + sizeC + ")", "Equal channel count", "Mismatch: " + channels.size() + " vs " + sizeC));
                        } else {
                            issues.add(ComplianceIssue.pass(cat, "OMERO Spec", "omero.channels count matches array channel dimension sizeC (" + sizeC + ")"));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void validateLabels(JsonNode rootAttrs, Path zarrRoot, List<ComplianceIssue> issues) {
        ComplianceCategory cat = ComplianceCategory.LABELS;
        JsonNode labelsAttr = rootAttrs.path("labels");
        File labelsDir = zarrRoot.resolve("labels").toFile();

        if (labelsAttr.isMissingNode() && !labelsDir.exists()) {
            issues.add(ComplianceIssue.info(cat, "OME-NGFF Labels Spec", "Optional labels metadata and directory not present.", "Labels metadata", "Not Present"));
        } else {
            issues.add(ComplianceIssue.pass(cat, "OME-NGFF Labels Spec", "Labels metadata/folder present and valid"));
        }
    }

    private void validatePlateWell(JsonNode rootAttrs, List<ComplianceIssue> issues) {
        ComplianceCategory cat = ComplianceCategory.PLATE_WELL;
        if (rootAttrs.has("plate") || rootAttrs.has("well")) {
            issues.add(ComplianceIssue.pass(cat, "OME-NGFF HCS Spec", "Plate/Well screening metadata present and valid"));
        } else {
            issues.add(ComplianceIssue.info(cat, "OME-NGFF HCS Spec", "Optional Plate/Well screening metadata not present.", "Plate/Well metadata", "Not Present"));
        }
    }

    private void validateTransitional(Path zarrRoot, List<ComplianceIssue> issues) {
        ComplianceCategory cat = ComplianceCategory.TRANSITIONAL;

        File b2rLayout = zarrRoot.resolve("bioformats2raw.layout").toFile();
        if (b2rLayout.exists()) {
            try {
                JsonNode b2r = mapper.readTree(b2rLayout);
                int ver = b2r.path("version").asInt(0);
                issues.add(ComplianceIssue.pass(cat, "bioformats2raw Layout", "bioformats2raw.layout present with layout version " + ver));
            } catch (Exception e) {
                issues.add(ComplianceIssue.warning(cat, "bioformats2raw Layout", "bioformats2raw.layout exists but is invalid JSON", "Valid JSON", e.getMessage()));
            }
        } else {
            issues.add(ComplianceIssue.info(cat, "bioformats2raw Layout", "bioformats2raw.layout not present", "Layout file", "Not Present"));
        }

        File companionXml = zarrRoot.resolve("OME").resolve("METADATA.ome.xml").toFile();
        if (companionXml.exists()) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                var builder = factory.newDocumentBuilder();
                var doc = builder.parse(companionXml);
                if ("OME".equalsIgnoreCase(doc.getDocumentElement().getLocalName())) {
                    issues.add(ComplianceIssue.pass(cat, "Companion OME-XML", "OME/METADATA.ome.xml is valid OME-XML document"));
                } else {
                    issues.add(ComplianceIssue.warning(cat, "Companion OME-XML", "OME/METADATA.ome.xml root element is not <OME>", "<OME>", doc.getDocumentElement().getNodeName()));
                }
            } catch (Exception e) {
                issues.add(ComplianceIssue.error(cat, "Companion OME-XML", "OME/METADATA.ome.xml is not valid XML: " + e.getMessage(), "Valid XML", "XML Parse Error"));
            }
        } else {
            issues.add(ComplianceIssue.info(cat, "Companion OME-XML", "Transitional OME/METADATA.ome.xml companion file not present", "Companion XML", "Not Present"));
        }
    }
}
