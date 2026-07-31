package org.ome.converter.service.validation;

import org.ome.converter.core.exception.ValidationException;
import org.ome.converter.core.registry.ConverterRegistry;

import java.io.File;

public class InputValidator {

    public void validateSourceFile(File sourceFile) throws ValidationException {
        if (sourceFile == null) {
            throw new ValidationException("Source file path cannot be null.");
        }
        if (!sourceFile.exists()) {
            throw new ValidationException("Source file does not exist: " + sourceFile.getAbsolutePath());
        }
        if (!sourceFile.isFile() || !sourceFile.canRead()) {
            throw new ValidationException("Source file is not readable or is a directory: " + sourceFile.getAbsolutePath());
        }
        if (sourceFile.length() == 0) {
            throw new ValidationException("Source file is empty (0 bytes): " + sourceFile.getName());
        }
        try {
            ConverterRegistry.getInstance().findProviderForFile(sourceFile);
        } catch (Exception e) {
            throw new ValidationException("Unsupported file format or no plugin registered for: " + sourceFile.getName());
        }
    }
}
