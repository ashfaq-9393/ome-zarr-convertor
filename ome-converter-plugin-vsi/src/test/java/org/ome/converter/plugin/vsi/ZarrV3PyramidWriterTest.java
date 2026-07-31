package org.ome.converter.plugin.vsi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ome.converter.core.model.ChunkSpec;
import org.ome.converter.core.model.ImageMetadata;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ZarrV3PyramidWriterTest {

    @Test
    void testSingleDestinationFolderZarrV3Structure(@TempDir Path tempDir) throws Exception {
        ZarrV3PyramidWriter writer = new ZarrV3PyramidWriter();
        Path zarrRoot = writer.initializeDatasetDirectory(tempDir, "sample_slide");

        assertThat(zarrRoot.getFileName().toString()).isEqualTo("sample_slide.zarr");
        assertThat(zarrRoot.toFile()).exists();

        ImageMetadata meta = new ImageMetadata(
            "sample_slide", 1024, 1024, 1, 1, 1,
            0.325, 0.325, 1.0, "micrometer", "micrometer", "micrometer",
            "uint16", 2, 2, Collections.emptyList()
        );

        // Write array metadata for level 0
        Path level0 = zarrRoot.resolve("0");
        writer.writeArrayMetadata(level0, meta, 0, ChunkSpec.defaultSpec(), 1024, 1024);

        File level0Json = level0.resolve("zarr.json").toFile();
        assertThat(level0Json).exists();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode arrayNode = mapper.readTree(level0Json);

        assertThat(arrayNode.get("zarr_format").asInt()).isEqualTo(3);
        assertThat(arrayNode.get("node_type").asText()).isEqualTo("array");
        assertThat(arrayNode.get("shape").get(3).asInt()).isEqualTo(1024); // y
        assertThat(arrayNode.get("shape").get(4).asInt()).isEqualTo(1024); // x

        // Write sample chunk
        byte[] dummyTile = new byte[512 * 512 * 2];
        long written = writer.writeChunkData(level0, 0, 0, 0, 0, 0, dummyTile, ChunkSpec.defaultSpec());
        assertThat(written).isGreaterThan(0);

        File chunkFile = level0.resolve("c").resolve("0").resolve("0").resolve("0").resolve("0").resolve("0").toFile();
        assertThat(chunkFile).exists();
        assertThat(chunkFile.length()).isEqualTo(written);
    }
}
