package org.ome.converter.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueDatasetPathResolverTest {

    @Test
    void testResolveUniquePathWhenTargetDoesNotExist(@TempDir Path tempDir) {
        Path uniquePath = UniqueDatasetPathResolver.resolveUniquePath(tempDir, "sample_slide.oir");
        assertThat(uniquePath.getFileName().toString()).isEqualTo("sample_slide.zarr");
    }

    @Test
    void testResolveUniquePathWhenTargetAlreadyExists(@TempDir Path tempDir) throws IOException {
        // Create initial target directory: sample_slide.zarr
        File initialDir = tempDir.resolve("sample_slide.zarr").toFile();
        initialDir.mkdirs();

        Path path1 = UniqueDatasetPathResolver.resolveUniquePath(tempDir, "sample_slide.oir");
        assertThat(path1.getFileName().toString()).isEqualTo("sample_slide_1.zarr");

        // Create second directory: sample_slide_1.zarr
        File firstDuplicateDir = path1.toFile();
        firstDuplicateDir.mkdirs();

        Path path2 = UniqueDatasetPathResolver.resolveUniquePath(tempDir, "sample_slide.oir");
        assertThat(path2.getFileName().toString()).isEqualTo("sample_slide_2.zarr");

        // Create third directory: sample_slide_2.zarr
        File secondDuplicateDir = path2.toFile();
        secondDuplicateDir.mkdirs();

        Path path3 = UniqueDatasetPathResolver.resolveUniquePath(tempDir, "sample_slide.oir");
        assertThat(path3.getFileName().toString()).isEqualTo("sample_slide_3.zarr");
    }
}
