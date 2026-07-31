package org.ome.converter.plugin.vsi;

import org.ome.converter.core.api.ConverterProvider;
import org.ome.converter.core.api.ImageConverter;

import java.io.File;
import java.util.List;

public class VsiConverterProvider implements ConverterProvider {
    private static final List<String> EXTENSIONS = List.of("vsi");

    @Override
    public String getFormatName() {
        return "Olympus CellSens VSI";
    }

    @Override
    public String getFormatDescription() {
        return "Olympus CellSens microscopic whole slide image format (.vsi)";
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
        return name.endsWith(".vsi");
    }

    @Override
    public ImageConverter createConverter() {
        return new VsiImageConverter();
    }
}
