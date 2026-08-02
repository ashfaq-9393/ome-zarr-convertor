package org.ome.converter.service.analysis.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

/**
 * Two-path gap analysis engine for OIR-to-OME metadata validation.
 * Extracted from canonical standalone repository: https://github.com/TausiqVarma/zarr-converter.git
 *
 * <p><b>Path 1 (Supported/Mapped):</b> Uses the universal mapping dictionary to check
 * whether formally mapped metadata fields appear in the correct OME schema locations
 * (e.g. {@code <Pixels>}, {@code <Objective>}, {@code <Laser>}).</p>
 *
 * <p><b>Path 2 (Dumped/StructuredAnnotations):</b> Uses a DOM parser to extract all
 * {@code <OriginalMetadata>} key-value pairs from the {@code <StructuredAnnotations>}
 * block and validates unmapped metadata strictly against that bucket.</p>
 */
public class GapAnalysisEngine implements GapAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(GapAnalysisEngine.class);

    private static final String ORIGINAL_METADATA_NS = "openmicroscopy.org/OriginalMetadata";

    private final ValidationDictionary dictionary;

    public GapAnalysisEngine(ValidationDictionary dictionary) {
        this.dictionary = dictionary;
    }

    /**
     * Runs the two-path gap analysis.
     *
     * @param rawMetadata the raw OIR metadata key-value pairs
     * @param omeXml      the full OME-XML string to validate against
     * @return ordered list of gap analysis results
     */
    @Override
    public List<GapResult> analyze(Map<String, Object> rawMetadata, String omeXml) {

        // ----- Path 2 setup: parse StructuredAnnotations into a fast lookup -----
        Map<String, String> dumpedMetadata = parseStructuredAnnotations(omeXml);
        logger.info("Parsed {} OriginalMetadata entries from StructuredAnnotations", dumpedMetadata.size());

        // ----- Extract the formal OME body (everything BEFORE StructuredAnnotations) -----
        String formalOmeBody = extractFormalBody(omeXml);

        // ----- Iterate over every raw metadata key -----
        List<GapResult> results = new ArrayList<>();

        for (Map.Entry<String, Object> entry : rawMetadata.entrySet()) {
            String rawKey = entry.getKey();
            String rawValue = String.valueOf(entry.getValue());

            ValidationRule rule = dictionary.findRule(rawKey);

            if (rule == null) {
                // Key is not in our dictionary at all.
                // Check if it was dumped into StructuredAnnotations anyway.
                GapResult dumpCheck = checkDumpedFallback(rawKey, rawValue, dumpedMetadata);
                results.add(dumpCheck);
                continue;
            }

            String dictKey = rule.getDictionaryKey();
            String status = rule.getStatus();
            String omeTarget = rule.getOmeTarget();

            switch (status) {
                case "MAPPED":
                    // PATH 1: Validate against the formal OME schema body
                    results.add(evaluateMapped(rawKey, rawValue, dictKey, rule, formalOmeBody));
                    break;
                case "UNMAPPED_OR_DROPPED":
                    // PATH 2: Validate strictly against StructuredAnnotations
                    results.add(evaluateDumped(rawKey, rawValue, dictKey, omeTarget, dumpedMetadata));
                    break;
                case "STRUCTURAL":
                    results.add(new GapResult(rawKey, rawValue, dictKey, status, omeTarget,
                            "N/A", GapResult.Verdict.SKIPPED));
                    break;
                default:
                    results.add(new GapResult(rawKey, rawValue, dictKey, status, omeTarget,
                            "N/A", GapResult.Verdict.UNTRACKED));
                    break;
            }
        }
        return results;
    }

    private GapResult evaluateMapped(String rawKey, String rawValue, String dictKey,
                                      ValidationRule rule, String formalBody) {
        String omeTarget = rule.getOmeTarget();
        String xmlTag = rule.getXmlTag();
        String xmlAttr = rule.getXmlAttribute();
                
        String searchVal = rawValue.trim();

        if (searchVal.isEmpty()) {
            return new GapResult(rawKey, rawValue, dictKey, "MAPPED", omeTarget,
                    "EMPTY_VALUE", GapResult.Verdict.SKIPPED);
        }

        // --- MINI-ROOM SEARCH ENHANCEMENT ---
        String searchArea = formalBody;
        if (!xmlTag.isEmpty()) {
            searchArea = extractTargetBlocks(formalBody, xmlTag);
            if (searchArea.isEmpty()) {
                return new GapResult(rawKey, rawValue, dictKey, "MAPPED", omeTarget,
                        "TARGET_ROOM_MISSING", GapResult.Verdict.CRITICAL_LOSS);
            }
        }

        // --- Attempt 1: Exact text search ---
        if (containsTargetValue(searchArea, xmlAttr, searchVal)) {
            return new GapResult(rawKey, rawValue, dictKey, "MAPPED", omeTarget,
                    searchVal, GapResult.Verdict.PRESERVED_FORMAL);
        }

        // --- Attempt 2: Normalized numeric match ---
        String normalized = normalizeNumeric(searchVal);
        if (normalized != null) {
            if (containsTargetValue(searchArea, xmlAttr, normalized)) {
                return new GapResult(rawKey, rawValue, dictKey, "MAPPED", omeTarget,
                        normalized + " (normalized)", GapResult.Verdict.PRESERVED_FORMAL);
            }
            try {
                double d = Double.parseDouble(searchVal);
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    String intStr = String.valueOf((long) d);
                    if (containsTargetValue(searchArea, xmlAttr, intStr)) {
                        return new GapResult(rawKey, rawValue, dictKey, "MAPPED", omeTarget,
                                intStr + " (truncated)", GapResult.Verdict.PRESERVED_FORMAL);
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        return new GapResult(rawKey, rawValue, dictKey, "MAPPED", omeTarget,
                "NOT_FOUND", GapResult.Verdict.CRITICAL_LOSS);
    }

    private boolean containsTargetValue(String body, String xmlAttr, String value) {
        if (xmlAttr != null && !xmlAttr.isEmpty()) {
            if (body.contains(xmlAttr + "=\"" + value + "\"")) {
                return true;
            }
            if (body.contains("<" + xmlAttr + ">" + value + "</" + xmlAttr + ">")) {
                return true;
            }
            return false;
        }
        
        return containsValue(body, value);
    }

    private boolean containsValue(String body, String value) {
        if (value.length() <= 2 && isNumeric(value)) {
            return body.contains("=\"" + value + "\"") || body.contains(">" + value + "<");
        }
        return body.contains(value);
    }

    private String extractTargetBlocks(String body, String target) {
        if (target == null || target.trim().isEmpty() || target.equals("N/A")) {
            return body;
        }
        
        StringBuilder blocks = new StringBuilder();
        String searchStr = "<" + target;
        int idx = 0;
        
        while ((idx = body.indexOf(searchStr, idx)) != -1) {
            if (idx + searchStr.length() < body.length()) {
                char nextChar = body.charAt(idx + searchStr.length());
                if (nextChar != ' ' && nextChar != '>' && nextChar != '/') {
                    idx += searchStr.length();
                    continue;
                }
            }
            
            int endTagIdx = body.indexOf("</" + target + ">", idx);
            int nextCloseIdx = body.indexOf(">", idx);
            
            if (endTagIdx != -1) {
                blocks.append(body.substring(idx, endTagIdx + ("</" + target + ">").length())).append("\n");
                idx = endTagIdx + ("</" + target + ">").length();
            } else if (nextCloseIdx != -1) {
                blocks.append(body.substring(idx, nextCloseIdx + 1)).append("\n");
                idx = nextCloseIdx + 1;
            } else {
                break;
            }
        }
        
        return blocks.toString();
    }

    private GapResult evaluateDumped(String rawKey, String rawValue, String dictKey,
                                      String omeTarget, Map<String, String> dumpedMetadata) {
        String dumpedValue = dumpedMetadata.get(rawKey);
        if (dumpedValue == null) {
            for (Map.Entry<String, String> dumpEntry : dumpedMetadata.entrySet()) {
                if (dumpEntry.getKey().endsWith(" " + rawKey) || dumpEntry.getKey().equals(rawKey)) {
                    dumpedValue = dumpEntry.getValue();
                    break;
                }
                String strippedRaw = stripFilenamePrefix(rawKey);
                if (dumpEntry.getKey().endsWith(" " + strippedRaw) || dumpEntry.getKey().equals(strippedRaw)) {
                    dumpedValue = dumpEntry.getValue();
                    break;
                }
            }
        }

        if (dumpedValue != null) {
            if (dumpedValue.equals(rawValue.trim()) || valuesMatchFuzzy(rawValue.trim(), dumpedValue)) {
                return new GapResult(rawKey, rawValue, dictKey, "DUMPED", omeTarget,
                        dumpedValue, GapResult.Verdict.PRESERVED_RAW);
            } else {
                return new GapResult(rawKey, rawValue, dictKey, "DUMPED", omeTarget,
                        "VALUE_MISMATCH: " + dumpedValue, GapResult.Verdict.CRITICAL_LOSS);
            }
        }

        return new GapResult(rawKey, rawValue, dictKey, "DUMPED", omeTarget,
                "NOT_IN_ANNOTATIONS", GapResult.Verdict.CRITICAL_LOSS);
    }

    private GapResult checkDumpedFallback(String rawKey, String rawValue,
                                           Map<String, String> dumpedMetadata) {
        String stripped = stripFilenamePrefix(rawKey);
        for (Map.Entry<String, String> dumpEntry : dumpedMetadata.entrySet()) {
            String dumpKey = dumpEntry.getKey();
            String dumpStripped = stripFilenamePrefix(dumpKey);
            if (dumpStripped.equalsIgnoreCase(stripped) || dumpKey.equalsIgnoreCase(rawKey)) {
                String dumpedValue = dumpEntry.getValue();
                if (dumpedValue.equals(rawValue.trim()) || valuesMatchFuzzy(rawValue.trim(), dumpedValue)) {
                    return new GapResult(rawKey, rawValue, "N/A", "UNTRACKED_BUT_DUMPED",
                            "OriginalMetadata", dumpedValue, GapResult.Verdict.PRESERVED_RAW);
                } else {
                    return new GapResult(rawKey, rawValue, "N/A", "UNTRACKED_BUT_DUMPED",
                            "OriginalMetadata", "VALUE_MISMATCH: " + dumpedValue, GapResult.Verdict.CRITICAL_LOSS);
                }
            }
        }
        return new GapResult(rawKey, rawValue, "N/A", "UNTRACKED",
                "N/A", "N/A", GapResult.Verdict.UNTRACKED);
    }

    private Map<String, String> parseStructuredAnnotations(String omeXml) {
        Map<String, String> result = new LinkedHashMap<>();
        if (omeXml == null || omeXml.isEmpty()) return result;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(omeXml)));

            NodeList annotations = doc.getElementsByTagName("XMLAnnotation");
            for (int i = 0; i < annotations.getLength(); i++) {
                Element annotation = (Element) annotations.item(i);
                String namespace = annotation.getAttribute("Namespace");
                if (!ORIGINAL_METADATA_NS.equals(namespace)) continue;

                NodeList omNodes = annotation.getElementsByTagName("OriginalMetadata");
                for (int j = 0; j < omNodes.getLength(); j++) {
                    Element om = (Element) omNodes.item(j);
                    NodeList keyNodes = om.getElementsByTagName("Key");
                    NodeList valNodes = om.getElementsByTagName("Value");
                    if (keyNodes.getLength() > 0 && valNodes.getLength() > 0) {
                        String key = keyNodes.item(0).getTextContent().trim();
                        String value = valNodes.item(0).getTextContent().trim();
                        if (!key.isEmpty()) {
                            result.put(key, value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse StructuredAnnotations from OME-XML, " +
                        "falling back to empty dump cache. Error: {}", e.getMessage());
        }
        return result;
    }

    private String extractFormalBody(String omeXml) {
        if (omeXml == null) return "";
        int idx = omeXml.indexOf("<StructuredAnnotations");
        if (idx > 0) {
            return omeXml.substring(0, idx);
        }
        return omeXml;
    }

    private String stripFilenamePrefix(String key) {
        if (key == null) return "";
        int oirIdx = key.indexOf(".oir ");
        if (oirIdx >= 0) {
            return key.substring(oirIdx + 5).trim();
        }
        return key.trim();
    }

    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String normalizeNumeric(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            double d = Double.parseDouble(value);
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean valuesMatchFuzzy(String raw, String found) {
        if (raw == null || found == null) return false;
        String rawClean = stripUnits(raw);
        String foundClean = stripUnits(found);
        if (rawClean.equals(foundClean)) return true;

        try {
            double r = Double.parseDouble(rawClean);
            double f = Double.parseDouble(foundClean);
            if (r == 0 && f == 0) return true;
            double diff = Math.abs(r - f);
            double maxVal = Math.max(Math.abs(r), Math.abs(f));
            return (diff / maxVal) < 0.001;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String stripUnits(String value) {
        if (value == null) return "";
        return value.trim()
                .replaceAll("\\s*(µm|um|nm|mm|cm|m|V|volt|Hz|s|ms|%)\\s*$", "")
                .replaceAll("\\.0+$", "")
                .trim();
    }
}
