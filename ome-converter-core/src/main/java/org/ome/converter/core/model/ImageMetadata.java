package org.ome.converter.core.model;

import java.util.List;

public record ImageMetadata(
    String imageName,
    int sizeX,
    int sizeY,
    int sizeZ,
    int sizeC,
    int sizeT,
    double physicalSizeX,
    double physicalSizeY,
    double physicalSizeZ,
    String unitX,
    String unitY,
    String unitZ,
    String pixelType,
    int bytesPerPixel,
    int pyramidLevels,
    List<ChannelInfo> channels
) {
    public record ChannelInfo(
        int index,
        String name,
        String hexColor,
        double windowMin,
        double windowMax
    ) {}
}
