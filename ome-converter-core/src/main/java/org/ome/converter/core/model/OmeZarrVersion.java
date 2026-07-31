package org.ome.converter.core.model;

public enum OmeZarrVersion {
    OME_ZARR_0_5("OME-Zarr 0.5 (Zarr v3)"),
    OME_ZARR_0_4("OME-Zarr 0.4 (Zarr v2)");

    private final String displayName;

    OmeZarrVersion(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static OmeZarrVersion fromDisplayName(String displayName) {
        if (displayName == null) return OME_ZARR_0_5;
        for (OmeZarrVersion version : values()) {
            if (version.displayName.equalsIgnoreCase(displayName) || version.name().equalsIgnoreCase(displayName)) {
                return version;
            }
        }
        return OME_ZARR_0_5;
    }
}
