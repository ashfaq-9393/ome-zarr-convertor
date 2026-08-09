package org.ome.converter.service.validation;

public enum OverallStatus {
    PASS("PASS", "All mandatory and recommended compliance checks passed cleanly."),
    WARNING("WARNING", "Dataset passed mandatory checks, but contains warnings for recommended metadata."),
    FAIL("FAIL", "Dataset failed one or more mandatory specification checks."),
    UNKNOWN("UNKNOWN", "OME-Zarr version could not be reliably determined."),
    VERSION_CONFLICT("VERSION_CONFLICT", "Dataset contains conflicting OME-NGFF version metadata.");

    private final String displayName;
    private final String description;

    OverallStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
