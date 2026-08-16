package org.ome.converter.plugin.vsi;

import org.ome.converter.core.exception.ConversionException;
import org.ome.converter.core.model.ChunkSpec;
import org.ome.converter.core.model.ImageMetadata;
import org.ome.converter.core.model.OmeZarrVersion;
import org.ome.converter.core.model.VendorMetadata;

import java.nio.file.Path;
import java.util.Map;

public interface OmeZarrWriterStrategy {
    Path initializeDatasetDirectory(Path targetDir, String datasetName) throws ConversionException;
    default Path initializeDatasetDirectory(Path targetDir, String datasetName, OmeZarrVersion version) throws ConversionException {
        return initializeDatasetDirectory(targetDir, datasetName);
    }
    void writeRootMetadata(Path zarrRoot, Map<String, Object> omeMetadata, VendorMetadata vendorMetadata) throws ConversionException;
    void writeArrayMetadata(Path levelPath, ImageMetadata meta, int level, ChunkSpec chunkSpec, int levelWidth, int levelHeight) throws ConversionException;
    long writeChunkData(Path levelPath, int t, int c, int z, int yChunkIndex, int xChunkIndex, byte[] rawTileBytes, ChunkSpec chunkSpec) throws Exception;
    void writeCompanionXml(Path zarrRoot, String xmlContent) throws ConversionException;
}
