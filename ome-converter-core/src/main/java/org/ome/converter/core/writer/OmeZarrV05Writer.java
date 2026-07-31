package org.ome.converter.core.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FileUtils;
import org.ome.converter.core.model.ImageMetadata;
import org.ome.converter.core.model.OmeZarrVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class OmeZarrV05Writer implements OmeZarrWriterStrategy {

    private static final Logger log = LoggerFactory.getLogger(OmeZarrV05Writer.class);
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public OmeZarrVersion getVersion() {
        return OmeZarrVersion.OME_ZARR_0_5;
    }

    @Override
    public void initializeRootMetadata(File zarrDir, ImageMetadata imageMetadata, Map<String, Object> rawVendorMetadata, String omeXmlContent) throws IOException {
        if (!zarrDir.exists()) {
            zarrDir.mkdirs();
        }

        Map<String, Object> rootJson = new LinkedHashMap<>();
        rootJson.put("zarr_format", 3);
        rootJson.put("node_type", "group");

        Map<String, Object> attributes = new LinkedHashMap<>();
        
        Map<String, Object> omeMeta = new LinkedHashMap<>();
        omeMeta.put("version", "0.5");

        List<Map<String, Object>> multiscales = new ArrayList<>();
        Map<String, Object> multiscale = new LinkedHashMap<>();
        multiscale.put("version", "0.5");
        multiscale.put("name", imageMetadata != null && imageMetadata.imageName() != null ? imageMetadata.imageName() : zarrDir.getName());

        List<Map<String, Object>> axes = new ArrayList<>();
        axes.add(Map.of("name", "t", "type", "time"));
        axes.add(Map.of("name", "c", "type", "channel"));
        axes.add(Map.of("name", "z", "type", "space", "unit", "micrometer"));
        axes.add(Map.of("name", "y", "type", "space", "unit", "micrometer"));
        axes.add(Map.of("name", "x", "type", "space", "unit", "micrometer"));
        multiscale.put("axes", axes);

        List<Map<String, Object>> datasets = new ArrayList<>();
        datasets.add(Map.of(
            "path", "0",
            "coordinateTransformations", List.of(
                Map.of(
                    "type", "scale",
                    "scale", List.of(1.0, 1.0, 
                        imageMetadata != null ? imageMetadata.physicalSizeZ() : 1.0,
                        imageMetadata != null ? imageMetadata.physicalSizeY() : 1.0,
                        imageMetadata != null ? imageMetadata.physicalSizeX() : 1.0)
                )
            )
        ));
        multiscale.put("datasets", datasets);

        multiscales.add(multiscale);
        omeMeta.put("multiscales", multiscales);
        attributes.put("ome", omeMeta);

        if (rawVendorMetadata != null && !rawVendorMetadata.isEmpty()) {
            attributes.put("vendor_custom_metadata", rawVendorMetadata);
        }

        rootJson.put("attributes", attributes);

        File zarrJsonFile = new File(zarrDir, "zarr.json");
        mapper.writeValue(zarrJsonFile, rootJson);
        log.info("Initialized OME-Zarr 0.5 root metadata at {}", zarrJsonFile.getAbsolutePath());

        if (omeXmlContent != null && !omeXmlContent.isBlank()) {
            File omeDir = new File(zarrDir, "OME");
            omeDir.mkdirs();
            File omeXmlFile = new File(omeDir, "METADATA.ome.xml");
            FileUtils.writeStringToFile(omeXmlFile, omeXmlContent, StandardCharsets.UTF_8);
        }
    }

    @Override
    public void initializeArrayLevel(File levelDir, ImageMetadata imageMetadata, int level, int tileWidth, int tileHeight) throws IOException {
        if (!levelDir.exists()) {
            levelDir.mkdirs();
        }

        int width = imageMetadata != null ? imageMetadata.sizeX() : 2048;
        int height = imageMetadata != null ? imageMetadata.sizeY() : 2048;
        int sizeZ = imageMetadata != null ? imageMetadata.sizeZ() : 1;
        int sizeC = imageMetadata != null ? imageMetadata.sizeC() : 1;
        int sizeT = imageMetadata != null ? imageMetadata.sizeT() : 1;

        Map<String, Object> arrayJson = new LinkedHashMap<>();
        arrayJson.put("zarr_format", 3);
        arrayJson.put("node_type", "array");
        arrayJson.put("data_type", imageMetadata != null && imageMetadata.pixelType() != null ? imageMetadata.pixelType() : "uint16");
        arrayJson.put("shape", List.of(sizeT, sizeC, sizeZ, height, width));

        Map<String, Object> chunkGrid = new LinkedHashMap<>();
        chunkGrid.put("name", "regular");
        chunkGrid.put("configuration", Map.of("chunk_shape", List.of(1, 1, 1, tileHeight, tileWidth)));
        arrayJson.put("chunk_grid", chunkGrid);

        Map<String, Object> chunkKeyEncoding = new LinkedHashMap<>();
        chunkKeyEncoding.put("name", "default");
        chunkKeyEncoding.put("configuration", Map.of("separator", "/"));
        arrayJson.put("chunk_key_encoding", chunkKeyEncoding);

        arrayJson.put("fill_value", 0);
        arrayJson.put("codecs", List.of(Map.of("name", "bytes", "configuration", Map.of("endian", "little"))));

        File arrayMetaFile = new File(levelDir, "zarr.json");
        mapper.writeValue(arrayMetaFile, arrayJson);
        log.info("Initialized array metadata for level {} at {}", level, arrayMetaFile.getAbsolutePath());
    }

    @Override
    public void writeChunk(File levelDir, int c, int z, int y, int x, byte[] pixelData) throws IOException {
        File chunkDir = new File(levelDir, String.format("0/%d/%d/%d", c, z, y));
        if (!chunkDir.exists()) {
            chunkDir.mkdirs();
        }
        File chunkFile = new File(chunkDir, String.valueOf(x));
        FileUtils.writeByteArrayToFile(chunkFile, pixelData);
    }
}
