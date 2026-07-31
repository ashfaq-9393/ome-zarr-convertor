package org.ome.converter.plugin.oir;

import org.ome.converter.core.api.ConverterProvider;
import org.ome.converter.core.api.ImageConverter;

import java.io.File;
import java.util.List;

public class OIRConverterProvider implements ConverterProvider {
    private static final List<String> EXTENSIONS = List.of("oir");

    @Override
    public String getFormatName() {
        return "Olympus OIR";
    }

    @Override
    public String getFormatDescription() {
        return "Olympus FluoView OIR microscopic image format (.oir)";
    }

    @Override
    public List<String> getSupportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public boolean supports(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".oir");
    }

    @Override
    public ImageConverter createConverter() {
        return new OIRImageConverter();
    }
}
