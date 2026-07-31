package org.ome.converter.core.writer;

import org.ome.converter.core.model.ImageMetadata;
import org.ome.converter.core.model.OmeZarrVersion;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public interface OmeZarrWriterStrategy {

    OmeZarrVersion getVersion();

    /**
     * Initializes the root group metadata structure (zarr.json for v3 or .zgroup/.zattrs for v2).
     */
    void initializeRootMetadata(File zarrDir, ImageMetadata imageMetadata, Map<String, Object> rawVendorMetadata, String omeXmlContent) throws IOException;

    /**
     * Initializes an array level metadata (e.g. 0/zarr.json for v3 or 0/.zarray for v2).
     */
    void initializeArrayLevel(File levelDir, ImageMetadata imageMetadata, int level, int tileWidth, int tileHeight) throws IOException;

    /**
     * Writes a binary chunk file to the appropriate hierarchy location in the Zarr container.
     */
    void writeChunk(File levelDir, int c, int z, int y, int x, byte[] pixelData) throws IOException;
}
