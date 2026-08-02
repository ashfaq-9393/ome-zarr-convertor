package org.ome.converter.plugin.vsi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

public class OmeZarrV04Writer implements OmeZarrWriterStrategy {
    private static final Logger log = LoggerFactory.getLogger(OmeZarrV04Writer.class);
    private final ObjectMapper mapper;

    public OmeZarrV04Writer() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public Path initializeDatasetDirectory(Path targetDir, String datasetName) throws ConversionException {
        try {
            Path zarrRoot = org.ome.converter.core.util.UniqueDatasetPathResolver.resolveUniquePath(targetDir, datasetName);
            Files.createDirectories(zarrRoot);

            // Write Zarr v2 root .zgroup
            Map<String, Object> zgroup = Map.of("zarr_format", 2);
            File zgroupFile = zarrRoot.resolve(".zgroup").toFile();
            mapper.writeValue(zgroupFile, zgroup);

            log.info("Initialized OME-Zarr 0.4 (Zarr v2) dataset output directory at: {}", zarrRoot.toAbsolutePath());
            return zarrRoot;
        } catch (Exception e) {
            throw new ConversionException("Failed to create target OME-Zarr 0.4 destination directory: " + targetDir, e);
        }
    }

    @Override
    public void writeRootMetadata(Path zarrRoot, Map<String, Object> omeMetadata, VendorMetadata vendorMetadata) throws ConversionException {
        try {
            Map<String, Object> zattrs = new LinkedHashMap<>();

            // Flatten OME NGFF 0.4 attributes & adjust version to 0.4
            if (omeMetadata.containsKey("multiscales")) {
                Object multiscalesObj = omeMetadata.get("multiscales");
                if (multiscalesObj instanceof List<?> list && !list.isEmpty()) {
                    List<Map<String, Object>> v4Multiscales = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> v4Group = new LinkedHashMap<>((Map<String, Object>) map);
                            v4Group.put("version", "0.4");
                            v4Multiscales.add(v4Group);
                        }
                    }
                    zattrs.put("multiscales", v4Multiscales);
                } else {
                    zattrs.put("multiscales", multiscalesObj);
                }
            }
            if (omeMetadata.containsKey("omero")) {
                zattrs.put("omero", omeMetadata.get("omero"));
            }

            if (vendorMetadata != null) {
                Map<String, Object> vendorCustom = new LinkedHashMap<>();
                vendorCustom.put("format_name", vendorMetadata.formatName());
                vendorCustom.put("vsi_global_tags", vendorMetadata.globalTags());
                vendorCustom.put("vsi_series_tags", vendorMetadata.seriesTags());
                if (vendorMetadata.rawXmlHeader() != null && !vendorMetadata.rawXmlHeader().isBlank()) {
                    vendorCustom.put("vsi_xml_header", vendorMetadata.rawXmlHeader());
                }
                zattrs.put("vendor_custom_metadata", vendorCustom);
            }

            File zattrsFile = zarrRoot.resolve(".zattrs").toFile();
            mapper.writeValue(zattrsFile, zattrs);
            log.info("Wrote Zarr v2 root .zattrs metadata to {}", zattrsFile.getAbsolutePath());
        } catch (Exception e) {
            throw new ConversionException("Failed to write root .zattrs node metadata", e);
        }
    }

    @Override
    public void writeArrayMetadata(Path levelPath, ImageMetadata meta, int level, ChunkSpec chunkSpec, int levelWidth, int levelHeight) throws ConversionException {
        try {
            Files.createDirectories(levelPath);
            Map<String, Object> zarray = new LinkedHashMap<>();
            zarray.put("zarr_format", 2);
            zarray.put("shape", List.of(meta.sizeT(), meta.sizeC(), meta.sizeZ(), levelHeight, levelWidth));
            zarray.put("chunks", List.of(1, 1, 1, Math.min(chunkSpec.tileHeight(), levelHeight), Math.min(chunkSpec.tileWidth(), levelWidth)));
            zarray.put("dtype", mapDataTypeV2(meta.pixelType()));

            if (chunkSpec.codec() != ChunkSpec.Codec.RAW) {
                Map<String, Object> compressor = new LinkedHashMap<>();
                compressor.put("id", "zlib");
                compressor.put("level", chunkSpec.compressionLevel());
                zarray.put("compressor", compressor);
            } else {
                zarray.put("compressor", null);
            }

            zarray.put("fill_value", 0);
            zarray.put("order", "C");
            zarray.put("dimension_separator", "/");

            File zarrayFile = levelPath.resolve(".zarray").toFile();
            mapper.writeValue(zarrayFile, zarray);

            // Write empty .zattrs for array level
            File levelZattrs = levelPath.resolve(".zattrs").toFile();
            mapper.writeValue(levelZattrs, Collections.emptyMap());

            log.info("Wrote Zarr v2 pyramid level {} array metadata (.zarray) to {}", level, zarrayFile.getAbsolutePath());
        } catch (Exception e) {
            throw new ConversionException("Failed to write Zarr v2 array metadata for level " + level, e);
        }
    }

    @Override
    public long writeChunkData(Path levelPath, int t, int c, int z, int yChunkIndex, int xChunkIndex, byte[] rawTileBytes, ChunkSpec chunkSpec) throws Exception {
        Path chunkDir = levelPath.resolve(String.valueOf(t))
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

    @Override
    public void writeCompanionXml(Path zarrRoot, String xmlContent) throws ConversionException {
        if (xmlContent == null || xmlContent.isBlank()) return;
        try {
            Path omeDir = zarrRoot.resolve("OME");
            Files.createDirectories(omeDir);
            Path companionFile = omeDir.resolve("METADATA.ome.xml");
            Files.writeString(companionFile, xmlContent);
            log.info("Wrote bioformats2raw companion OME-XML metadata node to {}", companionFile.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to write companion OME/METADATA.ome.xml file", e);
        }
    }

    private byte[] compressBytes(byte[] data, ChunkSpec chunkSpec) throws Exception {
        if (chunkSpec.codec() == ChunkSpec.Codec.RAW) {
            return data;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
        }
        return baos.toByteArray();
    }

    private String mapDataTypeV2(String pixelType) {
        if (pixelType == null) return "<u2";
        String lower = pixelType.toLowerCase();
        if (lower.contains("int8")) return lower.contains("u") ? "|u1" : "|i1";
        if (lower.contains("int16")) return lower.contains("u") ? "<u2" : "<i2";
        if (lower.contains("int32")) return lower.contains("u") ? "<u4" : "<i4";
        if (lower.contains("float")) return "<f4";
        if (lower.contains("double")) return "<f8";
        return "<u2";
    }
}
