package org.ome.converter.plugin.vsi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ome.converter.core.model.ChunkSpec;
import org.ome.converter.core.model.ImageMetadata;
import org.ome.converter.core.model.VendorMetadata;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OmeZarrV04WriterTest {

    @Test
    void testOmeZarrV04MetadataAndStructureGeneration(@TempDir Path tempDir) throws Exception {
        OmeZarrV04Writer writer = new OmeZarrV04Writer();
        Path zarrRoot = writer.initializeDatasetDirectory(tempDir, "test_v04_slide.zarr");

        File zgroupFile = zarrRoot.resolve(".zgroup").toFile();
        assertThat(zgroupFile).exists();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode zgroupNode = mapper.readTree(zgroupFile);
        assertThat(zgroupNode.get("zarr_format").asInt()).isEqualTo(2);

        Map<String, Object> dummyOme = Map.of(
            "version", "0.5",
            "multiscales", List.of(Map.of("name", "test_v04_slide.vsi", "version", "0.5"))
        );

        VendorMetadata vendorMeta = new VendorMetadata("Olympus CellSens VSI", Map.of("Tag1", "Val1"), Map.of(), "<raw_xml/>");
        writer.writeRootMetadata(zarrRoot, dummyOme, vendorMeta);
        writer.writeCompanionXml(zarrRoot, "<raw_xml/>");

        File zattrsFile = zarrRoot.resolve(".zattrs").toFile();
        assertThat(zattrsFile).exists();

        JsonNode zattrsNode = mapper.readTree(zattrsFile);
        JsonNode multiscales = zattrsNode.get("multiscales");
        assertThat(multiscales).isNotNull();
        assertThat(multiscales.get(0).get("version").asText()).isEqualTo("0.4");

        JsonNode vendorNode = zattrsNode.get("vendor_custom_metadata");
        assertThat(vendorNode).isNotNull();
        assertThat(vendorNode.get("vsi_global_tags").get("Tag1").asText()).isEqualTo("Val1");

        File companionXml = zarrRoot.resolve("OME").resolve("METADATA.ome.xml").toFile();
        assertThat(companionXml).exists();

        // Write array metadata for level 0
        ImageMetadata imgMeta = new ImageMetadata("slide", 1024, 1024, 1, 1, 1, 0.325, 0.325, 1.0, "micrometer", "micrometer", "micrometer", "uint16", 2, 1, List.of());
        Path level0 = zarrRoot.resolve("0");
        writer.writeArrayMetadata(level0, imgMeta, 0, ChunkSpec.defaultSpec(), 1024, 1024);

        File zarrayFile = level0.resolve(".zarray").toFile();
        assertThat(zarrayFile).exists();
        JsonNode zarrayNode = mapper.readTree(zarrayFile);
        assertThat(zarrayNode.get("zarr_format").asInt()).isEqualTo(2);
        assertThat(zarrayNode.get("dtype").asText()).isEqualTo("<u2");
    }
}
