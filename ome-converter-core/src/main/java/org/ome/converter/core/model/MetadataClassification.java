package org.ome.converter.core.model;

public enum MetadataClassification {
    MAPPED("Mapped (Standard OME-NGFF)"),
    RENAMED("Renamed (Semantic Match)"),
    VENDOR_METADATA("Vendor Metadata (Preserved)"),
    TRANSITIONAL_METADATA("Transitional Metadata (OME-XML Companion)"),
    MISSING("Missing / Unconverted"),
    POSSIBLE_MATCH("Possible Match Candidate"),
    UNKNOWN("Unmapped / Unknown Vendor Tag");

    private final String displayName;

    MetadataClassification(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
