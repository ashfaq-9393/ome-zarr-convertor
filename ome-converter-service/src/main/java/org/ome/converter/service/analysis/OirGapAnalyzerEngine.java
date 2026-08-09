package org.ome.converter.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ome.converter.core.model.GapAnalysisResult;
import org.ome.converter.core.model.ImageMetadata;
import org.ome.converter.core.model.OmeZarrVersion;
import org.ome.converter.core.model.VendorMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

public class OirGapAnalyzerEngine {
    private static final Logger log = LoggerFactory.getLogger(OirGapAnalyzerEngine.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, RuleSpec> prefixToDictKey = new LinkedHashMap<>();
    private final Map<String, RuleSpec> allRules = new LinkedHashMap<>();

    public static class RuleSpec {
        public String key;
        public String status;
        public String omeTarget;
        public String prettyKeyPrefix;
        public String suffixMatch;
        public List<String> altPrefixes;
    }

    public OirGapAnalyzerEngine() {
        loadDictionaryResources();
    }

    private void loadDictionaryResources() {
        String[] resources = {
            "/dictionary/validator_dictionary.json"
        };

        for (String resPath : resources) {
            try (InputStream is = getClass().getResourceAsStream(resPath)) {
                if (is != null) {
                    JsonNode root = mapper.readTree(is);
                    Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        String dictKey = entry.getKey();
                        JsonNode node = entry.getValue();
                        if (node.isObject()) {
                            RuleSpec spec = new RuleSpec();
                            spec.key = dictKey;
                            spec.status = node.has("status") ? node.get("status").asText() : "MAPPED";
                            spec.omeTarget = node.has("ome_target") ? node.get("ome_target").asText() : "";
                            spec.prettyKeyPrefix = node.has("pretty_key_prefix") ? node.get("pretty_key_prefix").asText().toLowerCase().trim() : "";
                            spec.suffixMatch = node.has("suffix_match") ? node.get("suffix_match").asText().toLowerCase().trim() : "";

                            if (!spec.prettyKeyPrefix.isEmpty()) {
                                prefixToDictKey.putIfAbsent(spec.prettyKeyPrefix, spec);
                            }

                            if (node.has("alt_prefixes") && node.get("alt_prefixes").isArray()) {
                                for (JsonNode altNode : node.get("alt_prefixes")) {
                                    String altPrefix = altNode.asText().toLowerCase().trim();
                                    if (!altPrefix.isEmpty()) {
                                        prefixToDictKey.putIfAbsent(altPrefix, spec);
                                    }
                                }
                            }

                            allRules.putIfAbsent(dictKey, spec);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not load dictionary resource {}: {}", resPath, e.getMessage());
            }
        }
    }

    public RuleSpec findRule(String rawKey) {
        if (rawKey == null) return null;
        String normalized = rawKey.toLowerCase().trim();

        if (normalized.startsWith("- ")) {
            normalized = normalized.substring(2).trim();
        }

        int oirSuffix = normalized.indexOf(".oir ");
        if (oirSuffix >= 0) {
            normalized = normalized.substring(oirSuffix + 5).trim();
        }

        String strippedPrefix = normalized;
        int colonIdx = strippedPrefix.indexOf(':');
        if (colonIdx >= 0) {
            strippedPrefix = strippedPrefix.substring(colonIdx + 1).trim();
        }

        // 0. Exact dict key lookup
        for (Map.Entry<String, RuleSpec> entry : allRules.entrySet()) {
            String k = entry.getKey();
            if (k.equalsIgnoreCase(normalized) || k.equalsIgnoreCase(rawKey) || k.equalsIgnoreCase(strippedPrefix) || k.equalsIgnoreCase("bf:" + strippedPrefix)) {
                return entry.getValue();
            }
        }

        // 1. Exact prefix lookup
        if (prefixToDictKey.containsKey(normalized)) {
            return prefixToDictKey.get(normalized);
        }
        if (prefixToDictKey.containsKey(strippedPrefix)) {
            return prefixToDictKey.get(strippedPrefix);
        }

        // 2. Longest prefix lookup
        RuleSpec bestMatch = null;
        int bestLength = 0;
        for (Map.Entry<String, RuleSpec> entry : prefixToDictKey.entrySet()) {
            String prefix = entry.getKey();
            if (normalized.startsWith(prefix) && prefix.length() > bestLength) {
                if (normalized.length() == prefix.length()
                    || normalized.charAt(prefix.length()) == ' '
                    || normalized.charAt(prefix.length()) == '.'
                    || normalized.charAt(prefix.length()) == '#') {
                    bestMatch = entry.getValue();
                    bestLength = prefix.length();
                }
            }
        }
        if (bestMatch != null) {
            return bestMatch;
        }

        // 3. Suffix-based lookup
        RuleSpec bestSuffixMatch = null;
        int bestSuffixLength = 0;
        for (RuleSpec spec : allRules.values()) {
            if (spec.suffixMatch != null && !spec.suffixMatch.isEmpty()) {
                if (spec.prettyKeyPrefix != null && !spec.prettyKeyPrefix.isEmpty()
                    && normalized.startsWith(spec.prettyKeyPrefix)
                    && normalized.endsWith(spec.suffixMatch)) {
                    if (spec.suffixMatch.length() > bestSuffixLength) {
                        bestSuffixMatch = spec;
                        bestSuffixLength = spec.suffixMatch.length();
                    }
                }
            }
        }

        return bestSuffixMatch;
    }

    private boolean isLossCandidate(String rawKey, RuleSpec rule, String rawVal) {
        if (rawVal == null || rawVal.isBlank() || rawVal.equalsIgnoreCase("null")) {
            return true;
        }
        if (rule != null && rule.status != null) {
            String st = rule.status.toUpperCase();
            if (st.contains("UNMAPPED") || st.contains("LOSS") || st.contains("DROPPED")) {
                return true;
            }
        }
        if (rawKey != null) {
            String k = rawKey.toLowerCase();
            if (k.startsWith("bf:")) k = k.substring(3);
            if (k.contains("creation") || k.contains("uuid") || k.contains("linked") ||
                k.contains("unmapped") || k.contains("unregistered") || k.contains("dropped") ||
                k.contains("uncalibrated") || k.contains("pixel length") ||
                k.contains("product build") || k.contains("snapshot count") ||
                k.contains("document company") || k.contains("document name") ||
                k.contains("internal id")) {
                return true;
            }
        }
        return false;
    }

    public GapAnalysisResult analyze(
        String datasetName,
        OmeZarrVersion version,
        ImageMetadata standardMeta,
        VendorMetadata vendorMeta,
        Path zarrRoot
    ) {
        log.info("Running TausiqVarma OIR Gap Analysis Engine for dataset: {}", datasetName);

        Map<String, String> rawTags = new LinkedHashMap<>();
        if (vendorMeta != null && vendorMeta.globalTags() != null) {
            rawTags.putAll(vendorMeta.globalTags());
        }

        int mapped = 0;
        int vendorDumped = 0;
        int loss = 0;
        List<GapAnalysisResult.GapAnalysisItemDetail> lostItems = new ArrayList<>();
        List<GapAnalysisResult.GapAnalysisItemDetail> allItems = new ArrayList<>();

        Set<String> standardDimensionKeys = Set.of(
            "SizeX", "SizeY", "SizeZ", "SizeC", "SizeT",
            "PhysicalSizeX", "PhysicalSizeY", "PhysicalSizeZ",
            "DimensionOrder", "PixelType"
        );

        for (Map.Entry<String, String> entry : rawTags.entrySet()) {
            String rawKey = entry.getKey();
            String rawVal = entry.getValue();

            RuleSpec rule = findRule(rawKey);
            boolean isStandardDimension = standardDimensionKeys.contains(rawKey);
            boolean isLoss = isLossCandidate(rawKey, rule, rawVal);

            if (isStandardDimension || (rule != null && "MAPPED".equalsIgnoreCase(rule.status))) {
                // Formal OME Mapped (Path 1)
                mapped++;
                allItems.add(new GapAnalysisResult.GapAnalysisItemDetail(
                    rawKey, rawVal != null ? rawVal : "", "MAPPED", "Mapped to formal OME-XML attribute."
                ));
            } else if (rule != null && "STRUCTURAL".equalsIgnoreCase(rule.status)) {
                // Internal structural linkage — skipped / non-data tag
                if (rawVal != null && !rawVal.isBlank() && !rawVal.equalsIgnoreCase("null")) {
                    vendorDumped++;
                    allItems.add(new GapAnalysisResult.GapAnalysisItemDetail(
                        rawKey, rawVal, "STRUCTURAL", "Internal structural linkage tag."
                    ));
                } else {
                    loss++;
                    GapAnalysisResult.GapAnalysisItemDetail item = new GapAnalysisResult.GapAnalysisItemDetail(
                        rawKey, "null", "LOSS (Missing)", "Tag failed value extraction during conversion."
                    );
                    lostItems.add(item);
                    allItems.add(item);
                }
            } else if (isLoss) {
                loss++;
                boolean isMissing = (rawVal == null || rawVal.isBlank() || rawVal.equalsIgnoreCase("null"));
                String statusStr = isMissing ? "LOSS (Missing)" : "LOSS (Unmapped)";
                String explanationStr = isMissing
                    ? "Tag failed value extraction during conversion."
                    : "Acquisition attribute unmapped in standard OME translation.";
                GapAnalysisResult.GapAnalysisItemDetail item = new GapAnalysisResult.GapAnalysisItemDetail(
                    rawKey, rawVal != null ? rawVal : "null", statusStr, explanationStr
                );
                lostItems.add(item);
                allItems.add(item);
            } else {
                // Vendor Custom Dumped namespace (Path 2: Preserved Raw)
                vendorDumped++;
                allItems.add(new GapAnalysisResult.GapAnalysisItemDetail(
                    rawKey, rawVal, "VENDOR_DUMPED", "Preserved raw vendor attribute in custom annotation namespace."
                ));
            }
        }

        int totalOriginal = Math.max(1, rawTags.size());
        Path reportPath = zarrRoot.getParent() != null ? zarrRoot.getParent() : zarrRoot;

        return new GapAnalysisResult(
            datasetName,
            version != null ? version : OmeZarrVersion.OME_ZARR_0_5,
            totalOriginal,
            mapped,
            vendorDumped,
            loss,
            lostItems,
            allItems,
            reportPath
        );
    }
}
