package org.ome.converter.core.util;

import org.ome.converter.core.model.OmeZarrVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility to resolve and automatically create format- and version-specific target subfolders.
 * E.g., for source file "sample.oir" and target version OME_ZARR_0_4,
 * it resolves/creates the subfolder "OIR to OME-Zarr 0.4" inside the target directory.
 * All existing files and unrelated folders in the target directory remain completely untouched.
 */
public class TargetSubfolderResolver {

    public static Path resolveTargetSubfolder(Path baseTargetDir, String sourceFileName, OmeZarrVersion targetVersion) {
        if (baseTargetDir == null) {
            throw new IllegalArgumentException("Base target directory cannot be null");
        }

        String format = extractFormatName(sourceFileName);
        String versionStr = (targetVersion == OmeZarrVersion.OME_ZARR_0_4) ? "0.4" : "0.5";
        String subfolderName = format + " to OME-Zarr " + versionStr;

        Path subfolderPath = baseTargetDir.resolve(subfolderName);
        File subfolderFile = subfolderPath.toFile();

        if (!subfolderFile.exists()) {
            try {
                Files.createDirectories(subfolderPath);
            } catch (IOException e) {
                return baseTargetDir;
            }
        }

        return subfolderPath;
    }

    public static String extractFormatName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "DATASET";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toUpperCase();
        }
        return "DATASET";
    }
}
