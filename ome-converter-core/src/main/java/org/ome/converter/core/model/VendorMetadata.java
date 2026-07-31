package org.ome.converter.core.model;

import java.util.Collections;
import java.util.Map;

public record VendorMetadata(
    String formatName,
    Map<String, String> globalTags,
    Map<String, Map<String, String>> seriesTags,
    String rawXmlHeader
) {
    public static VendorMetadata empty(String formatName) {
        return new VendorMetadata(formatName, Collections.emptyMap(), Collections.emptyMap(), "");
    }
}
