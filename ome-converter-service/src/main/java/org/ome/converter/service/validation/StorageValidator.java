package org.ome.converter.service.validation;

import org.ome.converter.core.exception.ValidationException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class StorageValidator {
    private static final long MIN_REQUIRED_BYTES = 100 * 1024 * 1024L; // 100 MB minimum check

    public void validateTargetDestination(Path targetDestinationDir, long estimatedRequiredBytes) throws ValidationException {
        if (targetDestinationDir == null) {
            throw new ValidationException("Target destination directory path cannot be null.");
        }
        File dir = targetDestinationDir.toFile();

        if (!dir.exists()) {
            try {
                Files.createDirectories(targetDestinationDir);
            } catch (Exception e) {
                throw new ValidationException("Unable to create target destination directory: " + targetDestinationDir.toAbsolutePath());
            }
        }

        if (!dir.isDirectory() || !Files.isWritable(targetDestinationDir)) {
            throw new ValidationException("Target destination directory is not writable or is not a valid folder: " + targetDestinationDir.toAbsolutePath());
        }

        long usableSpace = dir.getUsableSpace();
        long requiredSpace = estimatedRequiredBytes > 0 ? estimatedRequiredBytes : MIN_REQUIRED_BYTES;

        if (usableSpace < requiredSpace) {
            double freeMb = usableSpace / (1024.0 * 1024.0);
            double reqMb = requiredSpace / (1024.0 * 1024.0);
            throw new ValidationException(String.format("Insufficient disk space at destination! Free: %.2f MB, Required: %.2f MB", freeMb, reqMb));
        }
    }
}
