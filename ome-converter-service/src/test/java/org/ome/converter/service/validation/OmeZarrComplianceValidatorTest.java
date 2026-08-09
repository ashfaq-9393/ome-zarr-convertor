package org.ome.converter.service.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ome.converter.plugin.vsi.OmeZarrV04Writer;
import org.ome.converter.plugin.vsi.OmeZarrV05Writer;
import org.ome.converter.core.model.ChunkSpec;
import org.ome.converter.core.model.ImageMetadata;
import org.ome.converter.core.model.VendorMetadata;
import org.ome.converter.plugin.vsi.OmeNgffMetadataBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OmeZarrComplianceValidatorTest {

    private final OmeZarrComplianceService service = new OmeZarrComplianceService();

    @Test
    void testValidOmeZarrV04(@TempDir Path tempDir) throws Exception {
        Path zarrRoot = tempDir.resolve("valid_v04.ome.zarr");
        Files.createDirectories(zarrRoot.resolve("0"));

        Files.writeString(zarrRoot.resolve(".zgroup"), "{\"zarr_format\": 2}");
        Files.writeString(zarrRoot.resolve(".zattrs"), """
            {
              "multiscales": [
                {
                  "version": "0.4",
                  "name": "valid_v04",
                  "axes": [
                    {"name": "t", "type": "time"},
                    {"name": "c", "type": "channel"},
                    {"name": "z", "type": "space"},
                    {"name": "y", "type": "space"},
                    {"name": "x", "type": "space"}
                  ],
                  "datasets": [
                    {
                      "path": "0",
                      "coordinateTransformations": [{"type": "scale", "scale": [1.0, 1.0, 1.0, 0.325, 0.325]}]
                    }
                  ]
                }
              ]
            }
            """);

        Files.writeString(zarrRoot.resolve("0").resolve(".zarray"), """
            {
              "zarr_format": 2,
              "shape": [1, 1, 1, 512, 512],
              "chunks": [1, 1, 1, 256, 256],
              "dtype": "<u2",
              "compressor": null,
              "fill_value": 0,
              "order": "C",
              "dimension_separator": "/"
            }
            """);

        ComplianceResult result = service.validateDataset(zarrRoot);

        assertThat(result.overallStatus()).isEqualTo(OverallStatus.PASS);
        assertThat(result.errorCount()).isEqualTo(0);
        assertThat(result.detectedOmeVersion()).isEqualTo("0.4");
        assertThat(result.detectedZarrVersion()).isEqualTo("2");
    }

    @Test
    void testInvalidOmeZarrV04MissingDatasetPath(@TempDir Path tempDir) throws Exception {
        Path zarrRoot = tempDir.resolve("invalid_v04.ome.zarr");
        Files.createDirectories(zarrRoot);

        Files.writeString(zarrRoot.resolve(".zgroup"), "{\"zarr_format\": 2}");
        Files.writeString(zarrRoot.resolve(".zattrs"), """
            {
              "multiscales": [
                {
                  "version": "0.4",
                  "datasets": [{"path": "non_existent_level"}]
                }
              ]
            }
            """);

        ComplianceResult result = service.validateDataset(zarrRoot);

        assertThat(result.overallStatus()).isEqualTo(OverallStatus.FAIL);
        assertThat(result.errorCount()).isGreaterThan(0);
    }

    @Test
    void testValidOmeZarrV05(@TempDir Path tempDir) throws Exception {
        Path zarrRoot = tempDir.resolve("valid_v05.ome.zarr");
        Files.createDirectories(zarrRoot.resolve("0"));

        Files.writeString(zarrRoot.resolve("zarr.json"), """
            {
              "zarr_format": 3,
              "node_type": "group",
              "attributes": {
                "ome": {
                  "version": "0.5",
                  "multiscales": [
                    {
                      "name": "valid_v05",
                      "version": "0.5",
                      "axes": [
                        {"name": "t", "type": "time", "unit": "second"},
                        {"name": "c", "type": "channel"},
                        {"name": "z", "type": "space", "unit": "micrometer"},
                        {"name": "y", "type": "space", "unit": "micrometer"},
                        {"name": "x", "type": "space", "unit": "micrometer"}
                      ],
                      "datasets": [
                        {
                          "path": "0",
                          "coordinateTransformations": [{"type": "scale", "scale": [1.0, 1.0, 1.0, 0.2, 0.2]}]
                        }
                      ]
                    }
                  ]
                }
              }
            }
            """);

        Files.writeString(zarrRoot.resolve("0").resolve("zarr.json"), """
            {
              "zarr_format": 3,
              "node_type": "array",
              "shape": [1, 1, 1, 512, 512],
              "data_type": "uint16",
              "chunk_grid": { "name": "regular", "configuration": { "chunk_shape": [1, 1, 1, 256, 256] } },
              "chunk_key_encoding": { "name": "default", "configuration": { "separator": "/" } },
              "codecs": [ { "name": "bytes", "configuration": { "endian": "little" } } ],
              "fill_value": 0,
              "attributes": {}
            }
            """);

        ComplianceResult result = service.validateDataset(zarrRoot);

        assertThat(result.overallStatus()).isEqualTo(OverallStatus.PASS);
        assertThat(result.errorCount()).isEqualTo(0);
        assertThat(result.detectedOmeVersion()).isEqualTo("0.5");
        assertThat(result.detectedZarrVersion()).isEqualTo("3");
    }

    @Test
    void testInvalidOmeZarrV05MissingZarrJson(@TempDir Path tempDir) {
        Path zarrRoot = tempDir.resolve("missing_zarr_json.ome.zarr");
        zarrRoot.toFile().mkdirs();

        ComplianceResult result = service.validateDataset(zarrRoot);

        assertThat(result.overallStatus()).isEqualTo(OverallStatus.UNKNOWN);
    }

    @Test
    void testAxisDimensionMismatch(@TempDir Path tempDir) throws Exception {
        Path zarrRoot = tempDir.resolve("axis_mismatch.ome.zarr");
        Files.createDirectories(zarrRoot.resolve("0"));

        Files.writeString(zarrRoot.resolve(".zgroup"), "{\"zarr_format\": 2}");
        Files.writeString(zarrRoot.resolve(".zattrs"), """
            {
              "multiscales": [
                {
                  "version": "0.4",
                  "axes": [{"name": "y"}, {"name": "x"}],
                  "datasets": [{"path": "0"}]
                }
              ]
            }
            """);

        // 5D shape vs 2D axes
        Files.writeString(zarrRoot.resolve("0").resolve(".zarray"), """
            {
              "zarr_format": 2,
              "shape": [1, 1, 1, 512, 512],
              "chunks": [1, 1, 1, 256, 256],
              "dtype": "<u2"
            }
            """);

        ComplianceResult result = service.validateDataset(zarrRoot);

        assertThat(result.overallStatus()).isEqualTo(OverallStatus.FAIL);
        assertThat(result.issues()).anyMatch(i -> i.problem().contains("axes count"));
    }

    @Test
    void testInvalidCoordinateTransformation(@TempDir Path tempDir) throws Exception {
        Path zarrRoot = tempDir.resolve("invalid_transform.ome.zarr");
        Files.createDirectories(zarrRoot.resolve("0"));

        Files.writeString(zarrRoot.resolve(".zgroup"), "{\"zarr_format\": 2}");
        Files.writeString(zarrRoot.resolve(".zattrs"), """
            {
              "multiscales": [
                {
                  "version": "0.4",
                  "datasets": [
                    {
                      "path": "0",
                      "coordinateTransformations": [{"type": "scale", "scale": [-1.0, 0, 1.0, 1.0, 1.0]}]
                    }
                  ]
                }
              ]
            }
            """);

        Files.writeString(zarrRoot.resolve("0").resolve(".zarray"), """
            {
              "zarr_format": 2,
              "shape": [1, 1, 1, 512, 512],
              "chunks": [1, 1, 1, 256, 256],
              "dtype": "<u2"
            }
            """);

        ComplianceResult result = service.validateDataset(zarrRoot);

        assertThat(result.overallStatus()).isEqualTo(OverallStatus.FAIL);
        assertThat(result.issues()).anyMatch(i -> i.problem().contains("positive non-zero numbers"));
    }

    @Test
    void testConflictingVersionMetadata(@TempDir Path tempDir) throws Exception {
        Path zarrRoot = tempDir.resolve("conflict.ome.zarr");
        Files.createDirectories(zarrRoot);

        Files.writeString(zarrRoot.resolve("zarr.json"), """
            {
              "zarr_format": 3,
              "node_type": "group",
              "attributes": {
                "ome": {
                  "version": "0.5",
                  "multiscales": [
                    { "version": "0.4", "datasets": [{"path": "0"}] }
                  ]
                }
              }
            }
            """);

        ComplianceResult result = service.validateDataset(zarrRoot);

        assertThat(result.overallStatus()).isEqualTo(OverallStatus.VERSION_CONFLICT);
    }

    @Test
    void testDatasetWithWarningsAndAbsentOptionalMetadata(@TempDir Path tempDir) throws Exception {
        Path zarrRoot = tempDir.resolve("warnings.ome.zarr");
        Files.createDirectories(zarrRoot.resolve("0"));

        Files.writeString(zarrRoot.resolve("zarr.json"), """
            {
              "zarr_format": 3,
              "node_type": "group",
              "attributes": {
                "ome": {
                  "version": "0.5",
                  "multiscales": [
                    {
                      "name": "",
                      "version": "0.5",
                      "axes": [
                        {"name": "t", "type": "time"},
                        {"name": "c", "type": "channel"},
                        {"name": "z", "type": "space"},
                        {"name": "y", "type": "space"},
                        {"name": "x", "type": "space"}
                      ],
                      "datasets": [
                        {
                          "path": "0",
                          "coordinateTransformations": [{"type": "scale", "scale": [1.0, 1.0, 1.0, 0.2, 0.2]}]
                        }
                      ]
                    }
                  ]
                }
              }
            }
            """);

        Files.writeString(zarrRoot.resolve("0").resolve("zarr.json"), """
            {
              "zarr_format": 3,
              "node_type": "array",
              "shape": [1, 1, 1, 512, 512],
              "data_type": "uint16",
              "chunk_grid": { "name": "regular", "configuration": { "chunk_shape": [1, 1, 1, 256, 256] } },
              "chunk_key_encoding": { "name": "default", "configuration": { "separator": "/" } },
              "codecs": [ { "name": "bytes", "configuration": { "endian": "little" } } ],
              "fill_value": 0,
              "attributes": {}
            }
            """);

        ComplianceResult result = service.validateDataset(zarrRoot);

        // Missing unit and blank multiscale name produce WARNINGs, optional labels/plate are INFO -> overall status WARNING
        assertThat(result.overallStatus()).isEqualTo(OverallStatus.WARNING);
        assertThat(result.warningCount()).isGreaterThan(0);
        assertThat(result.errorCount()).isEqualTo(0);
    }

    // --- Converter Integration Tests for all 4 combinations ---

    @Test
    void testVsiToOmeZarr04ConverterComplianceOutput(@TempDir Path tempDir) throws Exception {
        ImageMetadata meta = createSampleMetadata();
        VendorMetadata vMeta = createSampleVendorMetadata();

        OmeZarrV04Writer writer = new OmeZarrV04Writer();
        Path zarrRoot = writer.initializeDatasetDirectory(tempDir, "vsi_04");

        OmeNgffMetadataBuilder builder = new OmeNgffMetadataBuilder();
        Map<String, Object> omeMeta = builder.buildOmeMetadata(meta);
        writer.writeRootMetadata(zarrRoot, omeMeta, vMeta);
        writer.writeArrayMetadata(zarrRoot.resolve("0"), meta, 0, ChunkSpec.defaultSpec(), 1024, 1024);

        ComplianceResult result = service.validateDataset(zarrRoot);

        assertThat(result.overallStatus()).isIn(OverallStatus.PASS, OverallStatus.WARNING);
        assertThat(result.detectedOmeVersion()).isEqualTo("0.4");
        assertThat(result.detectedZarrVersion()).isEqualTo("2");
    }

    @Test
    void testVsiToOmeZarr05ConverterComplianceOutput(@TempDir Path tempDir) throws Exception {
        ImageMetadata meta = createSampleMetadata();
        VendorMetadata vMeta = createSampleVendorMetadata();

        OmeZarrV05Writer writer = new OmeZarrV05Writer();
        Path zarrRoot = writer.initializeDatasetDirectory(tempDir, "vsi_05");

        OmeNgffMetadataBuilder builder = new OmeNgffMetadataBuilder();
        Map<String, Object> omeMeta = builder.buildOmeMetadata(meta);
        writer.writeRootMetadata(zarrRoot, omeMeta, vMeta);
        writer.writeArrayMetadata(zarrRoot.resolve("0"), meta, 0, ChunkSpec.defaultSpec(), 1024, 1024);

        ComplianceResult result = service.validateDataset(zarrRoot);

        assertThat(result.overallStatus()).isIn(OverallStatus.PASS, OverallStatus.WARNING);
        assertThat(result.detectedOmeVersion()).isEqualTo("0.5");
        assertThat(result.detectedZarrVersion()).isEqualTo("3");
    }

    @Test
    void testOirToOmeZarr04ConverterComplianceOutput(@TempDir Path tempDir) throws Exception {
        ImageMetadata meta = createSampleMetadata();

        OmeZarrV04Writer writer = new OmeZarrV04Writer();
        Path zarrRoot = writer.initializeDatasetDirectory(tempDir, "oir_04");

        OmeNgffMetadataBuilder builder = new OmeNgffMetadataBuilder();
        Map<String, Object> omeMeta = builder.buildOmeMetadata(meta);
        writer.writeRootMetadata(zarrRoot, omeMeta, null);
        writer.writeArrayMetadata(zarrRoot.resolve("0"), meta, 0, ChunkSpec.defaultSpec(), 1024, 1024);

        ComplianceResult result = service.validateDataset(zarrRoot);

        assertThat(result.overallStatus()).isIn(OverallStatus.PASS, OverallStatus.WARNING);
        assertThat(result.detectedOmeVersion()).isEqualTo("0.4");
        assertThat(result.detectedZarrVersion()).isEqualTo("2");
    }

    @Test
    void testOirToOmeZarr05ConverterComplianceOutput(@TempDir Path tempDir) throws Exception {
        ImageMetadata meta = createSampleMetadata();

        OmeZarrV05Writer writer = new OmeZarrV05Writer();
        Path zarrRoot = writer.initializeDatasetDirectory(tempDir, "oir_05");

        OmeNgffMetadataBuilder builder = new OmeNgffMetadataBuilder();
        Map<String, Object> omeMeta = builder.buildOmeMetadata(meta);
        writer.writeRootMetadata(zarrRoot, omeMeta, null);
        writer.writeArrayMetadata(zarrRoot.resolve("0"), meta, 0, ChunkSpec.defaultSpec(), 1024, 1024);

        ComplianceResult result = service.validateDataset(zarrRoot);

        assertThat(result.overallStatus()).isIn(OverallStatus.PASS, OverallStatus.WARNING);
        assertThat(result.detectedOmeVersion()).isEqualTo("0.5");
        assertThat(result.detectedZarrVersion()).isEqualTo("3");
    }

    private ImageMetadata createSampleMetadata() {
        return new ImageMetadata(
            "sample.vsi", 1024, 1024, 1, 1, 1,
            0.325, 0.325, 1.0, "micrometer", "micrometer", "micrometer",
            "uint16", 1, 1,
            List.of(new ImageMetadata.ChannelInfo(0, "DAPI", "0000FF", 0, 65535))
        );
    }

    private VendorMetadata createSampleVendorMetadata() {
        return new VendorMetadata(
            "Olympus CellSens VSI",
            Map.of("ResX", "0.325"),
            Map.of(),
            "<xml/>"
        );
    }
}
