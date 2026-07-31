package org.ome.converter.plugin.vsi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ome.converter.core.model.VendorMetadata;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VendorMetadataDumperTest {

    @Test
    void testVendorMetadataPreservationInRootZarrJson(@TempDir Path tempDir) throws Exception {
        Map<String, String> globalTags = Map.of(
            "Document.CreationTime", "2026-07-24T12:00:00Z",
            "Hardware.Objective.Magnification", "40x",
            "CellSens.Tag.0x1001", "OlympusDP74_RawBuffer"
        );
        Map<String, Map<String, String>> seriesTags = Map.of(
            "Series_0", Map.of("Width", "2048", "Height", "2048")
        );

        VendorMetadata vendorMeta = new VendorMetadata("Olympus CellSens VSI", globalTags, seriesTags, "<vsi_raw_header/>");

        ZarrV3PyramidWriter writer = new ZarrV3PyramidWriter();
        Path zarrRoot = writer.initializeDatasetDirectory(tempDir, "test_vendor_slide.zarr");

        Map<String, Object> dummyOme = Map.of("version", "0.5");
        writer.writeRootMetadata(zarrRoot, dummyOme, vendorMeta);

        File zarrJson = zarrRoot.resolve("zarr.json").toFile();
        assertThat(zarrJson).exists();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(zarrJson);

        assertThat(rootNode.get("zarr_format").asInt()).isEqualTo(3);
        assertThat(rootNode.get("node_type").asText()).isEqualTo("group");

        JsonNode vendorNode = rootNode.get("attributes").get("vendor_custom_metadata");
        assertThat(vendorNode).isNotNull();
        assertThat(vendorNode.get("format_name").asText()).isEqualTo("Olympus CellSens VSI");

        JsonNode globalNode = vendorNode.get("vsi_global_tags");
        assertThat(globalNode.get("Hardware.Objective.Magnification").asText()).isEqualTo("40x");
        assertThat(globalNode.get("CellSens.Tag.0x1001").asText()).isEqualTo("OlympusDP74_RawBuffer");
    }
}
