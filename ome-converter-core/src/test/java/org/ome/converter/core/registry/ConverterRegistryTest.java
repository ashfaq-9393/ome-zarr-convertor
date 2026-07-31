package org.ome.converter.core.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ome.converter.core.api.ConverterProvider;
import org.ome.converter.core.api.ImageConverter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConverterRegistryTest {

    private ConverterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ConverterRegistry.getInstance();
        registry.reloadProviders();
    }

    @Test
    void testManualProviderRegistrationAndLookup(@TempDir Path tempDir) throws Exception {
        Path mockFile = tempDir.resolve("sample_slide.vsi");
        Files.writeString(mockFile, "MOCK_VSI");

        ConverterProvider mockProvider = new ConverterProvider() {
            @Override
            public String getFormatName() { return "Test VSI"; }
            @Override
            public String getFormatDescription() { return "Test Mock VSI Plugin"; }
            @Override
            public List<String> getSupportedExtensions() { return List.of("vsi"); }
            @Override
            public boolean supports(File file) { return file != null && file.getName().endsWith(".vsi"); }
            @Override
            public ImageConverter createConverter() { return null; }
        };

        registry.registerProvider(mockProvider);
        ConverterProvider found = registry.findProviderForFile(mockFile.toFile());
        assertThat(found).isNotNull();
        assertThat(found.getFormatName()).isEqualTo("Test VSI");
    }
}
