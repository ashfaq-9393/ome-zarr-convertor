package org.ome.converter.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ome.converter.core.api.ConverterProvider;
import org.ome.converter.core.api.ImageConverter;
import org.ome.converter.core.api.ProgressObserver;
import org.ome.converter.core.model.ChunkSpec;
import org.ome.converter.core.model.ConversionRequest;
import org.ome.converter.core.model.ConversionResult;
import org.ome.converter.core.registry.ConverterRegistry;
import org.ome.converter.dao.impl.JsonFileAuditLogRepository;
import org.ome.converter.dao.impl.JsonFileJobRepository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ConversionOrchestratorTest {

    @Test
    void testEndToEndOrchestrationWithMockPlugin(@TempDir Path tempDir) throws Exception {
        Path mockSource = tempDir.resolve("sample_slide.vsimock");
        Files.writeString(mockSource, "VSI_MOCK_DATA_HEADER");

        Path mockDest = tempDir.resolve("output_zarr");
        Files.createDirectories(mockDest);

        // Register mock VSI provider
        ConverterProvider mockProvider = new ConverterProvider() {
            @Override
            public String getFormatName() { return "VSI Mock"; }
            @Override
            public String getFormatDescription() { return "VSI Mock Description"; }
            @Override
            public List<String> getSupportedExtensions() { return List.of("vsimock"); }
            @Override
            public boolean supports(File file) { return file != null && file.getName().endsWith(".vsimock"); }
            @Override
            public ImageConverter createConverter() {
                return (request, observer) -> ConversionResult.success(
                    request.jobId(),
                    request.targetDestinationDirectory().resolve("sample_slide.zarr"),
                    4, 4096, Duration.ofMillis(100)
                );
            }
        };
        ConverterRegistry.getInstance().registerProvider(mockProvider);

        Path jobsDb = tempDir.resolve("jobs.json");
        Path auditDb = tempDir.resolve("audit.json");
        ConversionOrchestrator orchestrator = new ConversionOrchestrator(
            new JsonFileJobRepository(jobsDb),
            new JsonFileAuditLogRepository(auditDb)
        );

        ConversionRequest request = new ConversionRequest(
            "JOB-TEST-1",
            mockSource,
            mockDest,
            ChunkSpec.defaultSpec(),
            true,
            2
        );

        Future<ConversionResult> future = orchestrator.submitConversion(request);
        ConversionResult result = future.get();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(ConversionResult.Status.SUCCESS);
        assertThat(result.totalTilesConverted()).isEqualTo(4);

        orchestrator.shutdown();
    }
}
