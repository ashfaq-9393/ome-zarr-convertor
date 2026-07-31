package org.ome.converter.plugin.template;

import org.ome.converter.core.api.ConverterProvider;
import org.ome.converter.core.api.ImageConverter;

import java.io.File;
import java.util.List;

public class TemplateConverterProvider implements ConverterProvider {
    @Override
    public String getFormatName() {
        return "Generic Microscope Plugin Template";
    }

    @Override
    public String getFormatDescription() {
        return "Boilerplate plugin for implementing third-party image format converters (OIR, CZI, ND2)";
    }

    @Override
    public List<String> getSupportedExtensions() {
        return List.of("oir", "czi", "nd2");
    }

    @Override
    public boolean supports(File file) {
        if (file == null || !file.exists()) return false;
        String name = file.getName().toLowerCase();
        return getSupportedExtensions().stream().anyMatch(name::endsWith);
    }

    @Override
    public ImageConverter createConverter() {
        return new TemplateImageConverter();
    }
}
