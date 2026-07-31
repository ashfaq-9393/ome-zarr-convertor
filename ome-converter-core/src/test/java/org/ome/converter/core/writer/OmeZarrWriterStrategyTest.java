package org.ome.converter.core.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ome.converter.core.model.ImageMetadata;
import org.ome.converter.core.model.OmeZarrVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OmeZarrWriterStrategyTest {

    @Test
    void testOmeZarrV05WriterInit(@TempDir Path tempDir) throws IOException {
        OmeZarrV05Writer writer = new OmeZarrV05Writer();
        assertThat(writer.getVersion()).isEqualTo(OmeZarrVersion.OME_ZARR_0_5);

        File zarrDir = tempDir.resolve("sample_v05.zarr").toFile();
        ImageMetadata metadata = new ImageMetadata(
            "sample_v05", 1024, 1024, 1, 1, 1,
            0.5, 0.5, 1.0, "µm", "µm", "µm",
            "uint16", 2, 1, List.of()
        );

        writer.initializeRootMetadata(zarrDir, metadata, Map.of("VendorTag", "Value123"), "<OME></OME>");
        File zarrJson = new File(zarrDir, "zarr.json");
        assertThat(zarrJson).exists();

        File level0 = new File(zarrDir, "0");
        writer.initializeArrayLevel(level0, metadata, 0, 512, 512);
        File levelZarrJson = new File(level0, "zarr.json");
        assertThat(levelZarrJson).exists();
    }

    @Test
    void testOmeZarrV04WriterInit(@TempDir Path tempDir) throws IOException {
        OmeZarrV04Writer writer = new OmeZarrV04Writer();
        assertThat(writer.getVersion()).isEqualTo(OmeZarrVersion.OME_ZARR_0_4);

        File zarrDir = tempDir.resolve("sample_v04.zarr").toFile();
        ImageMetadata metadata = new ImageMetadata(
            "sample_v04", 1024, 1024, 1, 1, 1,
            0.5, 0.5, 1.0, "µm", "µm", "µm",
            "uint16", 2, 1, List.of()
        );

        writer.initializeRootMetadata(zarrDir, metadata, Map.of("VendorTag", "Value123"), "<OME></OME>");
        File zgroup = new File(zarrDir, ".zgroup");
        File zattrs = new File(zarrDir, ".zattrs");
        assertThat(zgroup).exists();
        assertThat(zattrs).exists();

        File level0 = new File(zarrDir, "0");
        writer.initializeArrayLevel(level0, metadata, 0, 512, 512);
        File zarray = new File(level0, ".zarray");
        assertThat(zarray).exists();
    }
}
