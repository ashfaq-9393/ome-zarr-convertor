package org.ome.converter.service.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ome.converter.core.model.*;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataGapAnalysisTest {

    @Test
    void testSemanticMetadataDictionaryResolution() {
        Optional<String> concept1 = SemanticMetadataDictionary.findCanonicalConcept("ResX");
        assertThat(concept1).isPresent().contains("pixel_size_x");

        Optional<String> concept2 = SemanticMetadataDictionary.findCanonicalConcept("ExpTime_ms");
        assertThat(concept2).isPresent().contains("exposure_time");

        Optional<String> concept3 = SemanticMetadataDictionary.findCanonicalConcept("ObjectiveMag");
        assertThat(concept3).isPresent().contains("objective_magnification");
    }

    @Test
    void testFullMetadataGapAnalysisAndHtmlReportGeneration(@TempDir Path tempDir) {
        ImageMetadata standardMeta = new ImageMetadata(
            "sample_slide.vsi", 2048, 2048, 1, 3, 1,
            0.325, 0.325, 1.0, "micrometer", "micrometer", "micrometer",
            "uint16", 2, 2,
            List.of(
                new ImageMetadata.ChannelInfo(0, "DAPI", "0000FF", 0, 65535),
                new ImageMetadata.ChannelInfo(1, "FITC", "00FF00", 0, 65535),
                new ImageMetadata.ChannelInfo(2, "TRITC", "FF0000", 0, 65535)
            )
        );

        VendorMetadata vendorMeta = new VendorMetadata(
            "Olympus CellSens VSI",
            java.util.Map.of("ResX", "0.325", "ExpTime_ms", "150", "Hardware.Objective.Magnification", "40x"),
            java.util.Map.of("Series_0", java.util.Map.of("Width", "2048")),
            "<raw_xml/>"
        );

        Path zarrRoot = tempDir.resolve("sample_slide.zarr");
        zarrRoot.toFile().mkdirs();

        MetadataGapAnalyzerService service = new MetadataGapAnalyzerService();
        GapAnalysisResult result = service.analyzeAndReport("sample_slide.vsi", OmeZarrVersion.OME_ZARR_0_5, standardMeta, vendorMeta, zarrRoot);

        assertThat(result).isNotNull();
        assertThat(result.totalOriginalCount()).isGreaterThan(0);
        assertThat(result.mappedCount() + result.vendorDumpedCount() + result.lossCount()).isEqualTo(result.totalOriginalCount());

        File htmlReportFile = result.htmlReportPath().toFile();
        assertThat(htmlReportFile).exists();
        assertThat(htmlReportFile.length()).isGreaterThan(100L);
    }

    @Test
    void testOirMetadataGapAnalysisParity(@TempDir Path tempDir) {
        ImageMetadata standardMeta = new ImageMetadata(
            "sample_slide.oir", 2048, 2048, 1, 1, 1,
            0.2, 0.2, 1.0, "micrometer", "micrometer", "micrometer",
            "uint16", 2, 1,
            List.of(new ImageMetadata.ChannelInfo(0, "OIR Channel 1", "#00FF00", 0, 65535))
        );

        java.util.Map<String, String> rawTags = new java.util.LinkedHashMap<>();
        // 9 Mapped fields from universal dictionary
        List<String> mappedKeys = List.of(
            "Channel color", "Channel end wavelength", "Channel pinhole", "Channel start wavelength",
            "Creation date", "Detector gain", "Pixel Length X", "Pixel Length Y", "Z step"
        );
        for (String k : mappedKeys) {
            rawTags.put(k, "10.0");
        }
        // 24 Loss fields
        for (int i = 0; i < 24; i++) {
            rawTags.put("LostField_" + i, null);
        }
        // 867 Vendor Dumped fields
        for (int i = 0; i < 867; i++) {
            rawTags.put("VendorDumpedField_" + i, "val_" + i);
        }

        VendorMetadata vendorMeta = new VendorMetadata(
            "Olympus FluoView OIR",
            rawTags,
            java.util.Collections.emptyMap(),
            "<OME xmlns=\"http://www.openmicroscopy.org/Schemas/OME/2016-06\"></OME>"
        );

        Path zarrRoot = tempDir.resolve("sample_slide.zarr");
        zarrRoot.toFile().mkdirs();

        MetadataGapAnalyzerService service = new MetadataGapAnalyzerService();
        GapAnalysisResult result = service.analyzeAndReport("sample_slide.oir", OmeZarrVersion.OME_ZARR_0_5, standardMeta, vendorMeta, zarrRoot);

        assertThat(result).isNotNull();
        assertThat(result.totalOriginalCount()).isEqualTo(900);
        assertThat(result.mappedCount()).isEqualTo(9);
        assertThat(result.vendorDumpedCount()).isEqualTo(867);
        assertThat(result.lossCount()).isEqualTo(24);
    }
}
