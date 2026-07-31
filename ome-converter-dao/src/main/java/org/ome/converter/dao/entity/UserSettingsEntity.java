package org.ome.converter.dao.entity;

public record UserSettingsEntity(
    String lastSourceDirectory,
    String lastDestinationDirectory,
    int defaultTileWidth,
    int defaultTileHeight,
    String defaultCodec,
    int compressionLevel,
    int threadCount,
    boolean preserveVendorMetadata
) {
    public static UserSettingsEntity defaults() {
        return new UserSettingsEntity(
            System.getProperty("user.home"),
            System.getProperty("user.home"),
            512,
            512,
            "ZSTD",
            3,
            4,
            true
        );
    }
}
