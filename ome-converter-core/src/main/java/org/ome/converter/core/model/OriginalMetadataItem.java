package org.ome.converter.core.model;

public record OriginalMetadataItem(
    String hierarchyPath,
    String key,
    String value,
    String category,
    String vendor
) {
    public OriginalMetadataItem {
        if (key == null || key.isBlank()) {
            key = "Unknown_Key";
        }
        if (hierarchyPath == null || hierarchyPath.isBlank()) {
            hierarchyPath = key;
        }
        if (value == null) {
            value = "";
        }
        if (category == null || category.isBlank()) {
            category = "GLOBAL";
        }
        if (vendor == null || vendor.isBlank()) {
            vendor = "Generic Vendor";
        }
    }
}
