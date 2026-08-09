package org.ome.converter.service.validation;

public enum ComplianceCategory {
    STRUCTURE("Structure", "Zarr array and container layout verification"),
    VERSION("Version", "OME-NGFF version placement and consistency"),
    MULTISCALES("Multiscales", "Multiscales pyramid structure and dataset paths"),
    AXES("Axes", "Axis definitions, names, types, and units"),
    TRANSFORMATIONS("Transformations", "Coordinate transformations (scale, translation)"),
    OMERO("OMERO", "OMERO channel rendering and visualization metadata"),
    LABELS("Labels", "Image label sub-groups and metadata"),
    PLATE_WELL("Plate/Well", "High-content screening plate and well metadata"),
    TRANSITIONAL("Transitional", "bioformats2raw layout & companion XML metadata");

    private final String displayName;
    private final String description;

    ComplianceCategory(String displayName, String description) {
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
