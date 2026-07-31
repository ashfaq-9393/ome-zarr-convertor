package org.ome.converter.plugin.template;

import org.ome.converter.core.api.ImageConverter;
import org.ome.converter.core.api.ProgressObserver;
import org.ome.converter.core.exception.ConversionException;
import org.ome.converter.core.model.ConversionRequest;
import org.ome.converter.core.model.ConversionResult;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class TemplateImageConverter implements ImageConverter {
    @Override
    public ConversionResult convert(ConversionRequest request, ProgressObserver observer) throws ConversionException {
        Instant start = Instant.now();
        Path targetDir = request.targetDestinationDirectory();

        if (observer != null) {
            observer.onLog("INFO", "Executing template plugin conversion for: " + request.sourceFile().getFileName());
            observer.onProgress(100.0, 1, 1, "Template conversion placeholder finished");
        }

        return ConversionResult.success(
            request.jobId(),
            targetDir,
            1,
            1024,
            Duration.between(start, Instant.now())
        );
    }
}
