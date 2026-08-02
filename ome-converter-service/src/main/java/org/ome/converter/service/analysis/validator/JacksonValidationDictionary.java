package org.ome.converter.service.analysis.validator;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Concrete implementation of ValidationDictionary backed by Jackson JsonNode.
 * Ported from canonical GsonValidationDictionary in standalone repository.
 */
public class JacksonValidationDictionary implements ValidationDictionary {

    private final JsonNode dictionary;
    private final Map<String, String> prefixToDictKey = new LinkedHashMap<>();

    public JacksonValidationDictionary(JsonNode dictionary) {
        this.dictionary = dictionary;
        buildPrefixIndex();
    }

    private void buildPrefixIndex() {
        if (dictionary == null || !dictionary.isObject()) return;
        Iterator<Map.Entry<String, JsonNode>> fields = dictionary.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> dictEntry = fields.next();
            String dictKey = dictEntry.getKey();
            JsonNode entry = dictEntry.getValue();
            if (!entry.isObject()) continue;

            if (entry.has("pretty_key_prefix") && !entry.get("pretty_key_prefix").isNull()) {
                String prefix = entry.get("pretty_key_prefix").asText().toLowerCase().trim();
                if (!prefix.isEmpty()) {
                    prefixToDictKey.putIfAbsent(prefix, dictKey);
                }
            }

            if (entry.has("alt_prefixes") && entry.get("alt_prefixes").isArray()) {
                for (JsonNode altNode : entry.get("alt_prefixes")) {
                    String altPrefix = altNode.asText().toLowerCase().trim();
                    if (!altPrefix.isEmpty()) {
                        prefixToDictKey.putIfAbsent(altPrefix, dictKey);
                    }
                }
            }
        }
    }

    @Override
    public ValidationRule findRule(String rawKey) {
        String dictKey = findDictionaryKey(rawKey);
        if (dictKey == null) {
            return null;
        }

        JsonNode entry = dictionary.get(dictKey);
        if (entry == null || !entry.isObject()) {
            return null;
        }

        return new JacksonValidationRule(dictKey, entry);
    }

    private String findDictionaryKey(String rawKey) {
        if (rawKey == null) return null;
        String normalized = rawKey.toLowerCase().trim();

        if (normalized.startsWith("- ")) {
            normalized = normalized.substring(2).trim();
        }

        int oirSuffix = normalized.indexOf(".oir ");
        if (oirSuffix >= 0) {
            normalized = normalized.substring(oirSuffix + 5).trim();
        }

        // 0. Exact dict key lookup
        Iterator<String> fieldNames = dictionary.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            if (key.equalsIgnoreCase(normalized) || key.equalsIgnoreCase(rawKey)) {
                return key;
            }
        }

        // Exact match
        if (prefixToDictKey.containsKey(normalized)) {
            return prefixToDictKey.get(normalized);
        }

        // Longest-prefix match
        String bestMatch = null;
        int bestLength = 0;
        for (Map.Entry<String, String> entry : prefixToDictKey.entrySet()) {
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

        // Suffix-based matching
        String bestSuffixMatch = null;
        int bestSuffixLength = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = dictionary.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> dictEntry = fields.next();
            JsonNode entry = dictEntry.getValue();
            if (!entry.isObject()) continue;
            if (!entry.has("suffix_match") || entry.get("suffix_match").isNull()) continue;

            String suffix = entry.get("suffix_match").asText().toLowerCase().trim();
            String prefix = entry.has("pretty_key_prefix") && !entry.get("pretty_key_prefix").isNull()
                    ? entry.get("pretty_key_prefix").asText().toLowerCase().trim() : "";

            if (!prefix.isEmpty() && normalized.startsWith(prefix) && normalized.endsWith(suffix)) {
                if (suffix.length() > bestSuffixLength) {
                    bestSuffixMatch = dictEntry.getKey();
                    bestSuffixLength = suffix.length();
                }
            }
        }
        return bestSuffixMatch;
    }

    private static class JacksonValidationRule implements ValidationRule {
        private final String dictionaryKey;
        private final String status;
        private final String omeTarget;
        private final String xmlTag;
        private final String xmlAttribute;

        public JacksonValidationRule(String dictionaryKey, JsonNode entry) {
            this.dictionaryKey = dictionaryKey;
            this.status = entry.has("status") && !entry.get("status").isNull() 
                    ? entry.get("status").asText() : "UNKNOWN";
            this.omeTarget = entry.has("ome_target") && !entry.get("ome_target").isNull()
                    ? entry.get("ome_target").asText() : "";
            this.xmlTag = entry.has("xml_tag") && !entry.get("xml_tag").isNull()
                    ? entry.get("xml_tag").asText() : "";
            this.xmlAttribute = entry.has("xml_attribute") && !entry.get("xml_attribute").isNull()
                    ? entry.get("xml_attribute").asText() : "";
        }

        @Override
        public String getDictionaryKey() { return dictionaryKey; }
        @Override
        public String getStatus() { return status; }
        @Override
        public String getOmeTarget() { return omeTarget; }
        @Override
        public String getXmlTag() { return xmlTag; }
        @Override
        public String getXmlAttribute() { return xmlAttribute; }
    }
}
