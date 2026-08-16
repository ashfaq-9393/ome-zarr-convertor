package org.ome.converter.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ome.converter.core.model.OmeZarrVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TargetSubfolderResolverTest {

    @Test
    void testOirToOmeZarr04SubfolderCreation(@TempDir Path tempDir) {
        Path subfolder = TargetSubfolderResolver.resolveTargetSubfolder(tempDir, "sample_image.oir", OmeZarrVersion.OME_ZARR_0_4);

        assertThat(subfolder).isNotNull();
        assertThat(subfolder.getFileName().toString()).isEqualTo("OIR to OME-Zarr 0.4");
        assertThat(Files.isDirectory(subfolder)).isTrue();
    }

    @Test
    void testVsiToOmeZarr05SubfolderCreation(@TempDir Path tempDir) {
        Path subfolder = TargetSubfolderResolver.resolveTargetSubfolder(tempDir, "tissue_slide.vsi", OmeZarrVersion.OME_ZARR_0_5);

        assertThat(subfolder).isNotNull();
        assertThat(subfolder.getFileName().toString()).isEqualTo("VSI to OME-Zarr 0.5");
        assertThat(Files.isDirectory(subfolder)).isTrue();
    }

    @Test
    void testReusesExistingSubfolderWithoutTouchingOtherFiles(@TempDir Path tempDir) throws IOException {
        Path existingUnrelatedFolder = tempDir.resolve("UnrelatedFolder");
        Files.createDirectories(existingUnrelatedFolder);
        Path dummyFile = tempDir.resolve("my_report.pdf");
        Files.writeString(dummyFile, "Important Report");

        Path subfolder1 = TargetSubfolderResolver.resolveTargetSubfolder(tempDir, "test.oir", OmeZarrVersion.OME_ZARR_0_4);
        assertThat(subfolder1.getFileName().toString()).isEqualTo("OIR to OME-Zarr 0.4");

        Path subfolder2 = TargetSubfolderResolver.resolveTargetSubfolder(tempDir, "another.oir", OmeZarrVersion.OME_ZARR_0_4);
        assertThat(subfolder2).isEqualTo(subfolder1);

        // Verify other files and folders were completely untouched
        assertThat(Files.exists(existingUnrelatedFolder)).isTrue();
        assertThat(Files.exists(dummyFile)).isTrue();
        assertThat(Files.readString(dummyFile)).isEqualTo("Important Report");
    }

    @Test
    void testUniqueDatasetPathResolverWithVersion(@TempDir Path tempDir) {
        Path uniqueDatasetPath = UniqueDatasetPathResolver.resolveUniquePath(tempDir, "cell_sample.oir", OmeZarrVersion.OME_ZARR_0_4);

        assertThat(uniqueDatasetPath.getParent().getFileName().toString()).isEqualTo("OIR to OME-Zarr 0.4");
        assertThat(uniqueDatasetPath.getFileName().toString()).isEqualTo("cell_sample.zarr");
    }
}
