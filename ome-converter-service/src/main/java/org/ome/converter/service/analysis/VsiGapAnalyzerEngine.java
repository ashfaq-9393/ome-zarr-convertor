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

/**
 * Enterprise VSI Gap Analysis Engine utilizing static JSON dictionary mapping rulebooks
 * (validator_dictionary.json, universal_mapping_dictionary.json) combined with
 * SemanticMetadataDictionary for alias resolution.
 */
public class VsiGapAnalyzerEngine {
    private static final Logger log = LoggerFactory.getLogger(VsiGapAnalyzerEngine.class);
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

    public VsiGapAnalyzerEngine() {
        loadDictionaryResources();
    }

    private void loadDictionaryResources() {
        String[] resources = {
            "/dictionary/validator_dictionary.json",
            "/dictionary/universal_mapping_dictionary.json",
            "/dictionary/final_validator_mapping.json",
            "/dictionary/fixed_validator_mapping.json"
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

        int vsiSuffix = normalized.indexOf(".vsi ");
        if (vsiSuffix >= 0) {
            normalized = normalized.substring(vsiSuffix + 5).trim();
        }

        // 0. Exact dict key lookup
        for (Map.Entry<String, RuleSpec> entry : allRules.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(normalized) || entry.getKey().equalsIgnoreCase(rawKey)) {
                return entry.getValue();
            }
        }

        // 1. Exact prefix lookup
        if (prefixToDictKey.containsKey(normalized)) {
            return prefixToDictKey.get(normalized);
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

    public GapAnalysisResult analyze(
        String datasetName,
        OmeZarrVersion version,
        ImageMetadata standardMeta,
        VendorMetadata vendorMeta,
        Path zarrRoot
    ) {
        log.info("Running Pure Static Dictionary Gap Analysis Engine for VSI dataset: {}", datasetName);

        Map<String, String> rawTags = new LinkedHashMap<>();
        if (vendorMeta != null && vendorMeta.globalTags() != null) {
            rawTags.putAll(vendorMeta.globalTags());
        }

        // Collect standard dimension metadata attributes
        if (standardMeta != null) {
            rawTags.putIfAbsent("SizeX", String.valueOf(standardMeta.sizeX()));
            rawTags.putIfAbsent("SizeY", String.valueOf(standardMeta.sizeY()));
            rawTags.putIfAbsent("SizeZ", String.valueOf(standardMeta.sizeZ()));
            rawTags.putIfAbsent("SizeC", String.valueOf(standardMeta.sizeC()));
            rawTags.putIfAbsent("SizeT", String.valueOf(standardMeta.sizeT()));
            rawTags.putIfAbsent("PhysicalSizeX", String.valueOf(standardMeta.physicalSizeX()));
            rawTags.putIfAbsent("PhysicalSizeY", String.valueOf(standardMeta.physicalSizeY()));
            rawTags.putIfAbsent("PhysicalSizeZ", String.valueOf(standardMeta.physicalSizeZ()));
        }

        int mapped = 0;
        int vendorDumped = 0;
        int loss = 0;
        List<GapAnalysisResult.GapAnalysisItemDetail> lostItems = new ArrayList<>();

        for (Map.Entry<String, String> entry : rawTags.entrySet()) {
            String rawKey = entry.getKey();
            String rawVal = entry.getValue();

            RuleSpec rule = findRule(rawKey);

            if (rule != null && "MAPPED".equalsIgnoreCase(rule.status)) {
                // Formal OME Mapped via Static Dictionary
                mapped++;
            } else if (rule != null && "STRUCTURAL".equalsIgnoreCase(rule.status)) {
                // Internal structural linkage
                if (rawVal != null && !rawVal.isBlank() && !rawVal.equalsIgnoreCase("null")) {
                    vendorDumped++;
                } else {
                    loss++;
                    lostItems.add(new GapAnalysisResult.GapAnalysisItemDetail(
                        rawKey,
                        "null",
                        "LOSS (Missing)",
                        "Tag failed value extraction during VSI conversion."
                    ));
                }
            } else if (rawVal != null && !rawVal.isBlank() && !rawVal.equalsIgnoreCase("null")) {
                // Preserved Vendor Custom Dumped Metadata
                vendorDumped++;
            } else {
                loss++;
                lostItems.add(new GapAnalysisResult.GapAnalysisItemDetail(
                    rawKey,
                    "null",
                    "LOSS (Missing)",
                    "Tag failed value extraction during VSI conversion."
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
            reportPath
        );
    }
}
