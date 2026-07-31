package org.ome.converter.plugin.vsi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.ome.converter.core.api.ProgressObserver;
import org.ome.converter.core.exception.ConversionException;
import org.ome.converter.core.model.ChunkSpec;
import org.ome.converter.core.model.ImageMetadata;
import org.ome.converter.core.model.VendorMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class ZarrV3PyramidWriter {
    private static final Logger log = LoggerFactory.getLogger(ZarrV3PyramidWriter.class);
    private final ObjectMapper mapper;

    public ZarrV3PyramidWriter() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path initializeDatasetDirectory(Path targetDir, String datasetName) throws ConversionException {
        try {
            String sanitizedName = datasetName.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (!sanitizedName.toLowerCase().endsWith(".zarr")) {
                sanitizedName += ".zarr";
            }
            Path zarrRoot = targetDir.resolve(sanitizedName);
            Files.createDirectories(zarrRoot);
            log.info("Initialized dataset output directory at: {}", zarrRoot.toAbsolutePath());
            return zarrRoot;
        } catch (Exception e) {
            throw new ConversionException("Failed to create target OME-Zarr destination directory: " + targetDir, e);
        }
    }

    public void writeRootMetadata(Path zarrRoot, Map<String, Object> omeMetadata, VendorMetadata vendorMetadata) throws ConversionException {
        try {
            Map<String, Object> rootGroup = new LinkedHashMap<>();
            rootGroup.put("zarr_format", 3);
            rootGroup.put("node_type", "group");

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("ome", omeMetadata);

            if (vendorMetadata != null) {
                Map<String, Object> vendorCustom = new LinkedHashMap<>();
                vendorCustom.put("format_name", vendorMetadata.formatName());
                vendorCustom.put("vsi_global_tags", vendorMetadata.globalTags());
                vendorCustom.put("vsi_series_tags", vendorMetadata.seriesTags());
                if (vendorMetadata.rawXmlHeader() != null && !vendorMetadata.rawXmlHeader().isBlank()) {
                    vendorCustom.put("vsi_xml_header", vendorMetadata.rawXmlHeader());
                }
                attributes.put("vendor_custom_metadata", vendorCustom);
            }

            rootGroup.put("attributes", attributes);

            File zarrJsonFile = zarrRoot.resolve("zarr.json").toFile();
            mapper.writeValue(zarrJsonFile, rootGroup);
            log.info("Wrote Zarr v3 root node metadata to {}", zarrJsonFile.getAbsolutePath());
        } catch (Exception e) {
            throw new ConversionException("Failed to write root zarr.json node metadata", e);
        }
    }

    public void writeArrayMetadata(Path levelPath, ImageMetadata meta, int level, ChunkSpec chunkSpec, int levelWidth, int levelHeight) throws ConversionException {
        try {
            Files.createDirectories(levelPath);
            Map<String, Object> arrayNode = new LinkedHashMap<>();
            arrayNode.put("zarr_format", 3);
            arrayNode.put("node_type", "array");

            // Shape [t, c, z, y, x]
            arrayNode.put("shape", List.of(meta.sizeT(), meta.sizeC(), meta.sizeZ(), levelHeight, levelWidth));
            arrayNode.put("data_type", mapDataType(meta.pixelType()));

            // Chunk grid
            Map<String, Object> chunkGrid = new LinkedHashMap<>();
            chunkGrid.put("name", "regular");
            Map<String, Object> gridConfig = new LinkedHashMap<>();
            gridConfig.put("chunk_shape", List.of(1, 1, 1, Math.min(chunkSpec.tileHeight(), levelHeight), Math.min(chunkSpec.tileWidth(), levelWidth)));
            chunkGrid.put("configuration", gridConfig);
            arrayNode.put("chunk_grid", chunkGrid);

            // Chunk key encoding (Zarr v3 default uses 'c' directory prefix or default slash separation)
            Map<String, Object> chunkEncoding = new LinkedHashMap<>();
            chunkEncoding.put("name", "default");
            Map<String, Object> encConfig = new LinkedHashMap<>();
            encConfig.put("separator", "/");
            chunkEncoding.put("configuration", encConfig);
            arrayNode.put("chunk_key_encoding", chunkEncoding);

            // Codecs (Bytes + Compression)
            List<Map<String, Object>> codecs = new ArrayList<>();
            Map<String, Object> bytesCodec = new LinkedHashMap<>();
            bytesCodec.put("name", "bytes");
            bytesCodec.put("configuration", Map.of("endian", "little"));
            codecs.add(bytesCodec);

            Map<String, Object> compCodec = new LinkedHashMap<>();
            compCodec.put("name", chunkSpec.codec().getName());
            compCodec.put("configuration", Map.of("level", chunkSpec.compressionLevel()));
            codecs.add(compCodec);

            arrayNode.put("codecs", codecs);
            arrayNode.put("fill_value", 0);
            arrayNode.put("attributes", Collections.emptyMap());

            File levelJson = levelPath.resolve("zarr.json").toFile();
            mapper.writeValue(levelJson, arrayNode);
            log.info("Wrote pyramid level {} array metadata to {}", level, levelJson.getAbsolutePath());
        } catch (Exception e) {
            throw new ConversionException("Failed to write Zarr v3 array metadata for level " + level, e);
        }
    }

    public long writeChunkData(Path levelPath, int t, int c, int z, int yChunkIndex, int xChunkIndex, byte[] rawTileBytes, ChunkSpec chunkSpec) throws Exception {
        // Zarr v3 chunk key layout under c/t/c/z/y/x or default 0/0/0/y/x
        Path chunkDir = levelPath.resolve("c").resolve(String.valueOf(t))
                .resolve(String.valueOf(c))
                .resolve(String.valueOf(z))
                .resolve(String.valueOf(yChunkIndex));
        Files.createDirectories(chunkDir);

        Path chunkFile = chunkDir.resolve(String.valueOf(xChunkIndex));

        byte[] compressedData = compressBytes(rawTileBytes, chunkSpec);
        try (FileOutputStream fos = new FileOutputStream(chunkFile.toFile());
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            bos.write(compressedData);
        }
        return compressedData.length;
    }

    private byte[] compressBytes(byte[] data, ChunkSpec chunkSpec) throws Exception {
        if (chunkSpec.codec() == ChunkSpec.Codec.RAW) {
            return data;
        }
        // Compress using standard GZIP buffer for portability across environments
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
        }
        return baos.toByteArray();
    }

    private String mapDataType(String pixelType) {
        if (pixelType == null) return "uint16";
        String lower = pixelType.toLowerCase();
        if (lower.contains("int8")) return lower.contains("u") ? "uint8" : "int8";
        if (lower.contains("int16")) return lower.contains("u") ? "uint16" : "int16";
        if (lower.contains("int32")) return lower.contains("u") ? "uint32" : "int32";
        if (lower.contains("float")) return "float32";
        if (lower.contains("double")) return "float64";
        return "uint16";
    }
}
