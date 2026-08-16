package org.ome.converter.core.util;

import org.ome.converter.core.model.OmeZarrVersion;

import java.io.File;
import java.nio.file.Path;

/**
 * Utility to resolve unique dataset output directory paths.
 * Routes output placement into subfolders such as "OIR to OME-Zarr 0.4" or "VSI to OME-Zarr 0.5".
 * If a directory or file with the target name already exists in the subfolder,
 * it appends numerical suffixes (_1, _2, _3, _4, etc.) to keep both old and new files intact.
 */
public class UniqueDatasetPathResolver {

    public static Path resolveUniquePath(Path targetDir, String datasetName, OmeZarrVersion targetVersion) {
        Path resolvedSubfolder = TargetSubfolderResolver.resolveTargetSubfolder(targetDir, datasetName, targetVersion);
        return resolveUniquePath(resolvedSubfolder, datasetName);
    }

    public static Path resolveUniquePath(Path targetDir, String datasetName) {
        if (targetDir == null) {
            throw new IllegalArgumentException("Target directory cannot be null");
        }
        if (datasetName == null || datasetName.isBlank()) {
            datasetName = "dataset.zarr";
        }

        String sanitized = datasetName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String baseName = sanitized;
        String extension = ".zarr";

        if (sanitized.toLowerCase().endsWith(".zarr")) {
            baseName = sanitized.substring(0, sanitized.length() - 5);
        } else if (sanitized.contains(".")) {
            int lastDot = sanitized.lastIndexOf('.');
            baseName = sanitized.substring(0, lastDot);
        }

        if (baseName.isEmpty()) {
            baseName = "dataset";
        }

        File candidate = targetDir.resolve(baseName + extension).toFile();
        if (!candidate.exists()) {
            return candidate.toPath();
        }

        int suffix = 1;
        while (candidate.exists()) {
            candidate = targetDir.resolve(baseName + "_" + suffix + extension).toFile();
            suffix++;
        }

        return candidate.toPath();
    }
}
