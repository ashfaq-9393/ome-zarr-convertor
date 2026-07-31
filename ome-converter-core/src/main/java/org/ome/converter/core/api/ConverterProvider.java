package org.ome.converter.core.api;

import java.io.File;
import java.util.List;

public interface ConverterProvider {
    String getFormatName();
    String getFormatDescription();
    List<String> getSupportedExtensions();
    boolean supports(File file);
    ImageConverter createConverter();
}
