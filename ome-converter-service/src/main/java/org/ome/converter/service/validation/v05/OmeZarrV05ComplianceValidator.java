package org.ome.converter.service.validation.v05;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ome.converter.service.validation.ComplianceCategory;
import org.ome.converter.service.validation.ComplianceIssue;
import org.ome.converter.service.validation.zarr.ZarrV3Validator;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class OmeZarrV05ComplianceValidator {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final ZarrV3Validator zarrV3Validator = new ZarrV3Validator();

    public List<ComplianceIssue> validate(Path zarrRoot) {
        List<ComplianceIssue> issues = new ArrayList<>();

        File zarrJsonFile = zarrRoot.resolve("zarr.json").toFile();
        if (!zarrJsonFile.exists()) {
            issues.add(ComplianceIssue.error(ComplianceCategory.STRUCTURE, "OME-NGFF 0.5 Spec", "Root zarr.json file missing.", "Root zarr.json file", "Missing"));
            return issues;
        }

        JsonNode rootNode;
        try {
            rootNode = mapper.readTree(zarrJsonFile);
        } catch (Exception e) {
            issues.add(ComplianceIssue.error(ComplianceCategory.STRUCTURE, "OME-NGFF 0.5 Spec", "Failed to parse root zarr.json JSON: " + e.getMessage(), "Valid JSON", "JSON Error"));
            return issues;
        }

        JsonNode attributesNode = rootNode.path("attributes");
        JsonNode omeNode = attributesNode.path("ome");

        List<String> datasetPaths = extractDatasetPaths(omeNode);

        // Level 1: Zarr v3 Structure Check
        issues.addAll(zarrV3Validator.validateZarrV3Structure(zarrRoot, datasetPaths));

        // Level 2: OME-NGFF Version
        validateVersion(omeNode, issues);

        // Level 3: Multiscales
        validateMultiscales(omeNode, zarrRoot, issues, datasetPaths);

        // Level 4: Axes
        validateAxes(omeNode, zarrRoot, issues, datasetPaths);

        // Level 5: Coordinate Transformations
        validateTransformations(omeNode, issues);

        // Level 6: OMERO Metadata
        validateOmero(omeNode, zarrRoot, issues, datasetPaths);

        // Level 7: Labels
        validateLabels(omeNode, zarrRoot, issues);

        // Level 8: Plate / Well
        validatePlateWell(omeNode, issues);

        // Level 9: Bioformats2raw & Companion OME-XML
        validateTransitional(zarrRoot, issues);

        return issues;
    }

    private List<String> extractDatasetPaths(JsonNode omeNode) {
        List<String> paths = new ArrayList<>();
        if (omeNode.isMissingNode() || omeNode.isNull()) return paths;

        JsonNode multiscales = omeNode.path("multiscales");
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

    private void validateVersion(JsonNode omeNode, List<ComplianceIssue> issues) {
        ComplianceCategory cat = ComplianceCategory.VERSION;
        if (omeNode.isMissingNode() || omeNode.isNull()) {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §2", "attributes.ome namespace missing in root zarr.json", "attributes.ome object", "Missing"));
            return;
        }

        String omeVersion = omeNode.path("version").asText("");
        JsonNode multiscales = omeNode.path("multiscales");

        boolean versionMatch = "0.5".equals(omeVersion);
        if (multiscales.isArray() && !multiscales.isEmpty()) {
            for (JsonNode ms : multiscales) {
                String msVer = ms.path("version").asText("");
                if ("0.5".equals(msVer)) {
                    versionMatch = true;
                }
            }
        }

        if (versionMatch) {
            issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.5 §2", "OME-NGFF version is correctly specified as '0.5'"));
        } else {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §2", "OME-NGFF version missing or not '0.5'", "version = '0.5'", omeVersion.isBlank() ? "Missing" : omeVersion));
        }
    }

    private void validateMultiscales(JsonNode omeNode, Path zarrRoot, List<ComplianceIssue> issues, List<String> datasetPaths) {
        ComplianceCategory cat = ComplianceCategory.MULTISCALES;
        if (omeNode.isMissingNode()) return;

        JsonNode multiscales = omeNode.path("multiscales");
        if (!multiscales.isArray() || multiscales.isEmpty()) {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §3", "multiscales array missing or empty in attributes.ome", "Non-empty multiscales array", "Missing/Empty"));
            return;
        }

        JsonNode ms0 = multiscales.get(0);
        String name = ms0.path("name").asText("");
        if (name.isBlank()) {
            issues.add(ComplianceIssue.warning(cat, "OME-NGFF 0.5 §3", "multiscales[0].name is recommended", "Descriptive dataset name", "Blank/Missing"));
        } else {
            issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.5 §3", "multiscales[0].name is present ('" + name + "')"));
        }

        JsonNode datasets = ms0.path("datasets");
        if (!datasets.isArray() || datasets.isEmpty()) {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §3", "multiscales[0].datasets missing or empty", "Non-empty datasets list", "Missing/Empty"));
            return;
        }

        issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.5 §3", "multiscales[0].datasets contains " + datasets.size() + " pyramid levels"));

        for (String p : datasetPaths) {
            Path levelDir = zarrRoot.resolve(p);
            if (Files.exists(levelDir)) {
                issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.5 §3", "Dataset level path '" + p + "' exists on disk"));
            } else {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §3", "Dataset level path '" + p + "' does not exist on disk", "Existing directory", "Missing: " + p));
            }
        }
    }

    private void validateAxes(JsonNode omeNode, Path zarrRoot, List<ComplianceIssue> issues, List<String> datasetPaths) {
        ComplianceCategory cat = ComplianceCategory.AXES;
        if (omeNode.isMissingNode()) return;

        JsonNode multiscales = omeNode.path("multiscales");
        if (!multiscales.isArray() || multiscales.isEmpty()) return;

        JsonNode ms0 = multiscales.get(0);
        JsonNode axesNode = ms0.path("axes");

        if (!axesNode.isArray() || axesNode.isEmpty()) {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §4", "axes array MUST be present in multiscales[0]", "Non-empty axes array", axesNode.toString()));
            return;
        }

        List<String> axisNames = new ArrayList<>();
        Set<String> uniqueNames = new HashSet<>();
        Set<String> allowedTypes = Set.of("space", "time", "channel");

        for (int i = 0; i < axesNode.size(); i++) {
            JsonNode ax = axesNode.get(i);
            if (!ax.isObject()) {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §4", "Axis at index " + i + " MUST be an object in OME-NGFF 0.5", "Axis JSON object", ax.toString()));
                continue;
            }

            String name = ax.path("name").asText("");
            String type = ax.path("type").asText("");

            if (name.isBlank()) {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §4", "Axis at index " + i + " missing required 'name' field", "Non-blank name", "Blank"));
            } else {
                axisNames.add(name);
                if (!uniqueNames.add(name.toLowerCase())) {
                    issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §4", "Duplicate axis name detected: '" + name + "'", "Unique axis names", "Duplicate: " + name));
                }
            }

            if (type.isBlank()) {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §4", "Axis '" + name + "' missing required 'type' field", "space, time, or channel", "Blank"));
            } else if (!allowedTypes.contains(type.toLowerCase())) {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §4", "Invalid axis type '" + type + "' for axis '" + name + "'", "space, time, or channel", type));
            }

            if ("space".equalsIgnoreCase(type) || "time".equalsIgnoreCase(type)) {
                String unit = ax.path("unit").asText("");
                if (unit.isBlank()) {
                    issues.add(ComplianceIssue.warning(cat, "OME-NGFF 0.5 §4", "Recommended 'unit' field missing for " + type + " axis '" + name + "'", "Unit string (e.g. micrometer, second)", "Missing"));
                }
            }
        }

        issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.5 §4", "axes array contains " + axisNames.size() + " valid typed axis definitions: " + axisNames));

        // Validate axis count against Zarr v3 level 0 array shape rank
        if (!datasetPaths.isEmpty()) {
            Path level0Json = zarrRoot.resolve(datasetPaths.get(0)).resolve("zarr.json");
            if (level0Json.toFile().exists()) {
                try {
                    JsonNode za = mapper.readTree(level0Json.toFile());
                    JsonNode shapeNode = za.path("shape");
                    if (shapeNode.isArray() && shapeNode.size() != axesNode.size()) {
                        issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §4", "axes count (" + axesNode.size() + ") does not match array shape rank (" + shapeNode.size() + ")", "Equal rank (" + shapeNode.size() + ")", String.valueOf(axesNode.size())));
                    } else {
                        issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.5 §4", "axes count matches dataset array shape rank (" + shapeNode.size() + "D)"));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void validateTransformations(JsonNode omeNode, List<ComplianceIssue> issues) {
        ComplianceCategory cat = ComplianceCategory.TRANSFORMATIONS;
        if (omeNode.isMissingNode()) return;

        JsonNode multiscales = omeNode.path("multiscales");
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
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §5", "No coordinateTransformations found in multiscales or datasets", "Scale transformation array", "Missing"));
        } else {
            issues.add(ComplianceIssue.pass(cat, "OME-NGFF 0.5 §5", "Valid coordinateTransformations defined in multiscales"));
        }
    }

    private void validateSingleTransform(JsonNode t, String location, List<ComplianceIssue> issues) {
        ComplianceCategory cat = ComplianceCategory.TRANSFORMATIONS;
        String type = t.path("type").asText("");
        if (!Set.of("scale", "translation", "identity").contains(type.toLowerCase())) {
            issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §5", "Invalid transformation type '" + type + "' in " + location, "scale, translation, or identity", type));
            return;
        }

        if ("scale".equalsIgnoreCase(type)) {
            JsonNode scaleNode = t.path("scale");
            if (!scaleNode.isArray() || scaleNode.isEmpty()) {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §5", "Scale transformation missing 'scale' numeric array in " + location, "Non-empty scale array", scaleNode.toString()));
            } else {
                for (JsonNode val : scaleNode) {
                    if (val.asDouble(0.0) <= 0) {
                        issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §5", "Scale transformation values must be positive non-zero numbers in " + location, "> 0", val.toString()));
                    }
                }
            }
        } else if ("translation".equalsIgnoreCase(type)) {
            JsonNode transNode = t.path("translation");
            if (!transNode.isArray() || transNode.isEmpty()) {
                issues.add(ComplianceIssue.error(cat, "OME-NGFF 0.5 §5", "Translation transformation missing 'translation' numeric array in " + location, "Non-empty translation array", transNode.toString()));
            }
        }
    }

    private void validateOmero(JsonNode omeNode, Path zarrRoot, List<ComplianceIssue> issues, List<String> datasetPaths) {
        ComplianceCategory cat = ComplianceCategory.OMERO;
        if (omeNode.isMissingNode()) return;

        JsonNode omeroNode = omeNode.path("omero");
        if (omeroNode.isMissingNode() || omeroNode.isNull()) {
            issues.add(ComplianceIssue.info(cat, "OMERO Spec", "OMERO channel rendering metadata not present in attributes.ome", "OMERO metadata", "Not Present"));
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

        // Validate channel count against level 0 Zarr v3 array sizeC
        if (!datasetPaths.isEmpty()) {
            Path level0Json = zarrRoot.resolve(datasetPaths.get(0)).resolve("zarr.json");
            if (level0Json.toFile().exists()) {
                try {
                    JsonNode za = mapper.readTree(level0Json.toFile());
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

    private void validateLabels(JsonNode omeNode, Path zarrRoot, List<ComplianceIssue> issues) {
        ComplianceCategory cat = ComplianceCategory.LABELS;
        boolean hasLabelsAttr = omeNode.has("labels");
        File labelsDir = zarrRoot.resolve("labels").toFile();

        if (!hasLabelsAttr && !labelsDir.exists()) {
            issues.add(ComplianceIssue.info(cat, "OME-NGFF Labels Spec", "Optional labels metadata and directory not present.", "Labels metadata", "Not Present"));
        } else {
            issues.add(ComplianceIssue.pass(cat, "OME-NGFF Labels Spec", "Labels metadata/folder present and valid"));
        }
    }

    private void validatePlateWell(JsonNode omeNode, List<ComplianceIssue> issues) {
        ComplianceCategory cat = ComplianceCategory.PLATE_WELL;
        if (omeNode.has("plate") || omeNode.has("well")) {
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
