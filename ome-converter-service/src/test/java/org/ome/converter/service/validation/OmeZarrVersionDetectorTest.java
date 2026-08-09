package org.ome.converter.service.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OmeZarrVersionDetectorTest {

    private final OmeZarrVersionDetector detector = new OmeZarrVersionDetector();

    @Test
    void testDetectValidOmeZarrV04(@TempDir Path tempDir) throws Exception {
        Path zarrRoot = tempDir.resolve("sample.ome.zarr");
        Files.createDirectories(zarrRoot);

        Files.writeString(zarrRoot.resolve(".zgroup"), "{\"zarr_format\": 2}");
        Files.writeString(zarrRoot.resolve(".zattrs"), """
            {
              "multiscales": [
                {
                  "version": "0.4",
                  "name": "sample",
                  "datasets": [{"path": "0"}]
                }
              ]
            }
            """);

        OmeZarrVersionDetector.DetectionResult result = detector.detectVersion(zarrRoot);

        assertThat(result.omeZarrVersion()).isEqualTo("0.4");
        assertThat(result.zarrFormatVersion()).isEqualTo("2");
    }

    @Test
    void testDetectValidOmeZarrV05(@TempDir Path tempDir) throws Exception {
        Path zarrRoot = tempDir.resolve("sample.ome.zarr");
        Files.createDirectories(zarrRoot);

        Files.writeString(zarrRoot.resolve("zarr.json"), """
            {
              "zarr_format": 3,
              "node_type": "group",
              "attributes": {
                "ome": {
                  "version": "0.5",
                  "multiscales": [
                    {
                      "name": "sample",
                      "version": "0.5",
                      "datasets": [{"path": "0"}]
                    }
                  ]
                }
              }
            }
            """);

        OmeZarrVersionDetector.DetectionResult result = detector.detectVersion(zarrRoot);

        assertThat(result.omeZarrVersion()).isEqualTo("0.5");
        assertThat(result.zarrFormatVersion()).isEqualTo("3");
    }

    @Test
    void testDetectVersionConflict(@TempDir Path tempDir) throws Exception {
        Path zarrRoot = tempDir.resolve("sample.ome.zarr");
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

        OmeZarrVersionDetector.DetectionResult result = detector.detectVersion(zarrRoot);

        assertThat(result.omeZarrVersion()).isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void testDetectUnknownVersionOnEmptyDir(@TempDir Path tempDir) {
        Path zarrRoot = tempDir.resolve("empty.zarr");
        zarrRoot.toFile().mkdirs();

        OmeZarrVersionDetector.DetectionResult result = detector.detectVersion(zarrRoot);

        assertThat(result.omeZarrVersion()).isEqualTo("UNKNOWN");
        assertThat(result.zarrFormatVersion()).isEqualTo("UNKNOWN");
    }
}
