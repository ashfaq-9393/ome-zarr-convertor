package org.ome.converter.service.impl;

import org.ome.converter.core.api.ConverterProvider;
import org.ome.converter.core.api.ImageConverter;
import org.ome.converter.core.api.ProgressObserver;
import org.ome.converter.core.event.AsyncEventBus;
import org.ome.converter.core.event.GapAnalysisEvent;
import org.ome.converter.core.event.LogEvent;
import org.ome.converter.core.event.ProgressEvent;
import org.ome.converter.core.exception.ConversionException;
import org.ome.converter.core.model.ConversionRequest;
import org.ome.converter.core.model.ConversionResult;
import org.ome.converter.core.registry.ConverterRegistry;
import org.ome.converter.dao.api.AuditLogRepository;
import org.ome.converter.dao.api.JobRepository;
import org.ome.converter.dao.entity.JobEntity;
import org.ome.converter.dao.impl.JsonFileAuditLogRepository;
import org.ome.converter.dao.impl.JsonFileJobRepository;
import org.ome.converter.service.validation.InputValidator;
import org.ome.converter.service.validation.StorageValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConversionOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ConversionOrchestrator.class);

    private final JobRepository jobRepository;
    private final AuditLogRepository auditLogRepository;
    private final InputValidator inputValidator;
    private final StorageValidator storageValidator;
    private final ExecutorService executorService;
    private final Map<String, Future<ConversionResult>> activeFutures = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancelSignals = new ConcurrentHashMap<>();

    public ConversionOrchestrator() {
        this(new JsonFileJobRepository(), new JsonFileAuditLogRepository());
    }

    public ConversionOrchestrator(JobRepository jobRepository, AuditLogRepository auditLogRepository) {
        this.jobRepository = jobRepository;
        this.auditLogRepository = auditLogRepository;
        this.inputValidator = new InputValidator();
        this.storageValidator = new StorageValidator();
        this.executorService = Executors.newFixedThreadPool(4, new ThreadFactory() {
            private int count = 0;
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "ConversionWorker-" + (++count));
            }
        });
    }

    public Future<ConversionResult> submitConversion(ConversionRequest request) throws ConversionException {
        File sourceFile = request.sourceFile().toFile();
        inputValidator.validateSourceFile(sourceFile);
        storageValidator.validateTargetDestination(request.targetDestinationDirectory(), sourceFile.length());

        ConverterProvider provider = ConverterRegistry.getInstance().findProviderForFile(sourceFile);
        ImageConverter converter = provider.createConverter();

        AtomicBoolean cancelSignal = new AtomicBoolean(false);
        cancelSignals.put(request.jobId(), cancelSignal);

        JobEntity initialEntity = new JobEntity(
            request.jobId(),
            request.sourceFile().toAbsolutePath().toString(),
            request.targetDestinationDirectory().toAbsolutePath().toString(),
            "RUNNING",
            0,
            0,
            Instant.now(),
            null,
            null
        );
        jobRepository.save(initialEntity);
        auditLogRepository.log("JOB_SUBMITTED", "Submitted conversion for file: " + sourceFile.getName());

        AsyncEventBus.getInstance().publishLog(LogEvent.info(request.jobId(), "Job submitted successfully: " + request.jobId()));

        Callable<ConversionResult> task = () -> {
            ProgressObserver observer = new ProgressObserver() {
                @Override
                public void onProgress(double percentage, long tilesProcessed, long totalTiles, String currentTask) {
                    boolean cancelled = cancelSignal.get();
                    AsyncEventBus.getInstance().publishProgress(
                        new ProgressEvent(request.jobId(), percentage, tilesProcessed, totalTiles, currentTask, percentage >= 100.0, cancelled)
                    );
                }

                @Override
                public void onLog(String level, String message) {
                    LogEvent.Level lvl = LogEvent.Level.INFO;
                    try {
                        lvl = LogEvent.Level.valueOf(level.toUpperCase());
                    } catch (Exception ignored) {}
                    AsyncEventBus.getInstance().publishLog(new LogEvent(request.jobId(), lvl, message, Instant.now()));
                }

                @Override
                public boolean isCancelled() {
                    return cancelSignal.get();
                }
            };

            try {
                ConversionResult result = converter.convert(request, observer);

                // Run Metadata Gap Analysis Framework
                if (result.status() == ConversionResult.Status.SUCCESS && result.outputZarrPath() != null) {
                    try {
                        observer.onProgress(98.0, result.totalTilesConverted(), result.totalTilesConverted(), "[98.0%] Running Metadata Gap Analysis...");
                        observer.onLog("INFO", "Running Metadata Gap Analysis for " + request.targetVersion().getDisplayName() + "...");

                        org.ome.converter.service.analysis.MetadataGapAnalyzerService analyzer = new org.ome.converter.service.analysis.MetadataGapAnalyzerService();
                        org.ome.converter.core.model.GapAnalysisResult gapResult = analyzer.analyzeAndReport(
                            sourceFile.getName(),
                            request.targetVersion(),
                            result.standardMetadata(),
                            result.vendorMetadata(),
                            result.outputZarrPath()
                        );

                        observer.onLog("INFO", "Metadata Gap Analysis Complete! Total: " + gapResult.totalOriginalCount() + " | Mapped: " + gapResult.mappedCount() + " | Dumped: " + gapResult.vendorDumpedCount() + " | Loss: " + gapResult.lossCount());
                        observer.onLog("INFO", "HTML Report saved at: " + gapResult.htmlReportPath().toAbsolutePath());

                        AsyncEventBus.getInstance().publishGapAnalysis(new GapAnalysisEvent(request.jobId(), gapResult));

                        result = ConversionResult.successWithAnalysis(
                            result.jobId(),
                            result.outputZarrPath(),
                            result.totalTilesConverted(),
                            result.totalBytesWritten(),
                            result.executionDuration(),
                            gapResult
                        );
                    } catch (Exception gapEx) {
                        log.warn("Failed to complete Metadata Gap Analysis: {}", gapEx.getMessage(), gapEx);
                    }
                }

                JobEntity updatedEntity = new JobEntity(
                    request.jobId(),
                    request.sourceFile().toAbsolutePath().toString(),
                    result.outputZarrPath().toAbsolutePath().toString(),
                    result.status().name(),
                    result.totalTilesConverted(),
                    result.totalBytesWritten(),
                    initialEntity.startTime(),
                    Instant.now(),
                    result.errorMessage()
                );
                jobRepository.save(updatedEntity);
                auditLogRepository.log("JOB_FINISHED", "Job finished with status: " + result.status());

                return result;
            } catch (Exception e) {
                log.error("Job execution failed: {}", request.jobId(), e);
                JobEntity failedEntity = new JobEntity(
                    request.jobId(),
                    request.sourceFile().toAbsolutePath().toString(),
                    request.targetDestinationDirectory().toAbsolutePath().toString(),
                    "FAILED",
                    0,
                    0,
                    initialEntity.startTime(),
                    Instant.now(),
                    e.getMessage()
                );
                jobRepository.save(failedEntity);
                auditLogRepository.log("JOB_FAILED", "Job failed: " + e.getMessage());
                throw e;
            } finally {
                activeFutures.remove(request.jobId());
                cancelSignals.remove(request.jobId());
            }
        };

        Future<ConversionResult> future = executorService.submit(task);
        activeFutures.put(request.jobId(), future);
        return future;
    }

    public boolean cancelJob(String jobId) {
        AtomicBoolean cancelSignal = cancelSignals.get(jobId);
        if (cancelSignal != null) {
            cancelSignal.set(true);
            Future<ConversionResult> future = activeFutures.get(jobId);
            if (future != null) {
                future.cancel(true);
            }
            auditLogRepository.log("JOB_CANCELLED", "User requested cancellation for job: " + jobId);
            AsyncEventBus.getInstance().publishLog(LogEvent.warn(jobId, "Cancellation requested for job: " + jobId));
            return true;
        }
        return false;
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}
