package org.ome.converter.core.model;

public record ConvertedMetadataItem(
    String locationPath,
    String key,
    String value,
    String namespace,
    String specVersion
) {
    public ConvertedMetadataItem {
        if (key == null || key.isBlank()) {
            key = "Unknown_Key";
        }
        if (locationPath == null || locationPath.isBlank()) {
            locationPath = key;
        }
        if (value == null) {
            value = "";
        }
        if (namespace == null || namespace.isBlank()) {
            namespace = "OME_NGFF_CORE";
        }
        if (specVersion == null || specVersion.isBlank()) {
            specVersion = "OME-Zarr 0.5";
        }
    }
}
