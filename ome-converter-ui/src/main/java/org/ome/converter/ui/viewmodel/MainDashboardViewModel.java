package org.ome.converter.ui.viewmodel;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.ome.converter.core.event.AsyncEventBus;
import org.ome.converter.core.event.EventListener;
import org.ome.converter.core.event.GapAnalysisEvent;
import org.ome.converter.core.event.LogEvent;
import org.ome.converter.core.event.ProgressEvent;
import org.ome.converter.core.model.ChunkSpec;
import org.ome.converter.core.model.ConversionRequest;
import org.ome.converter.core.model.GapAnalysisResult;
import org.ome.converter.dao.api.SettingsRepository;
import org.ome.converter.dao.entity.JobEntity;
import org.ome.converter.dao.entity.UserSettingsEntity;
import org.ome.converter.dao.impl.JsonFileSettingsRepository;
import org.ome.converter.service.impl.ConversionOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class MainDashboardViewModel implements EventListener {
    private static final Logger log = LoggerFactory.getLogger(MainDashboardViewModel.class);

    private final StringProperty sourceFormat = new SimpleStringProperty("Olympus CellSens VSI (.vsi)");
    private final StringProperty sourceFilePath = new SimpleStringProperty("");
    private final StringProperty targetDestinationPath = new SimpleStringProperty("");
    private final DoubleProperty progressPercentage = new SimpleDoubleProperty(0.0);
    private final StringProperty statusText = new SimpleStringProperty("Ready");
    private final StringProperty throughputText = new SimpleStringProperty("0.00 MB/s");
    private final BooleanProperty converting = new SimpleBooleanProperty(false);
    private final BooleanProperty preserveVendorMetadata = new SimpleBooleanProperty(true);
    private final IntegerProperty cpuThreads = new SimpleIntegerProperty(Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors())));
    private final ObjectProperty<org.ome.converter.core.model.OmeZarrVersion> targetVersion = new SimpleObjectProperty<>(org.ome.converter.core.model.OmeZarrVersion.OME_ZARR_0_5);

    // Gap Analysis Numerical Properties
    private final StringProperty dashboardSubtitle = new SimpleStringProperty("Dataset: Select a file and run conversion | Target Spec: OME-Zarr");
    private final StringProperty totalFields = new SimpleStringProperty("--");
    private final StringProperty mappedFields = new SimpleStringProperty("--");
    private final StringProperty vendorDumped = new SimpleStringProperty("--");
    private final StringProperty lossFields = new SimpleStringProperty("--");

    private final StringProperty badgeMappedText = new SimpleStringProperty("Mapped: 0");
    private final StringProperty badgeVendorText = new SimpleStringProperty("Vendor Custom (Dumped): 0");
    private final StringProperty badgeLossText = new SimpleStringProperty("Loss: 0");
    private final StringProperty badgeAllText = new SimpleStringProperty("All Fields (0)");
    private final StringProperty lostHeader = new SimpleStringProperty("Lost Metadata Inventory (Displaying 0 Lost / Missing Fields)");

    // Compliance UI Properties
    private final StringProperty complianceDatasetPath = new SimpleStringProperty("");
    private final StringProperty detectedOmeVersion = new SimpleStringProperty("--");
    private final StringProperty detectedZarrVersion = new SimpleStringProperty("--");
    private final StringProperty complianceOverallStatus = new SimpleStringProperty("--");
    private final StringProperty complianceErrorsCount = new SimpleStringProperty("0");
    private final StringProperty complianceWarningsCount = new SimpleStringProperty("0");
    private final StringProperty complianceInfoCount = new SimpleStringProperty("0");
    private final StringProperty complianceProgressText = new SimpleStringProperty("");
    private final BooleanProperty validatingCompliance = new SimpleBooleanProperty(false);
    private final ObjectProperty<org.ome.converter.service.validation.ComplianceResult> latestComplianceResult = new SimpleObjectProperty<>(null);

    private final java.util.Map<org.ome.converter.service.validation.ComplianceCategory, StringProperty> categoryStatusProperties = new java.util.EnumMap<>(org.ome.converter.service.validation.ComplianceCategory.class);

    private final ObservableList<GapAnalysisResult.GapAnalysisItemDetail> lostItems = FXCollections.observableArrayList();
    private final ObservableList<GapAnalysisResult.GapAnalysisItemDetail> allItems = FXCollections.observableArrayList();
    private final ObservableList<String> logMessages = FXCollections.observableArrayList();
    private final ObservableList<JobEntity> jobHistory = FXCollections.observableArrayList();

    private final ConversionOrchestrator orchestrator;
    private final SettingsRepository settingsRepository;
    private final org.ome.converter.service.validation.OmeZarrComplianceService complianceService;
    private String currentJobId;

    public MainDashboardViewModel() {
        this(new ConversionOrchestrator(), new JsonFileSettingsRepository());
    }

    public MainDashboardViewModel(ConversionOrchestrator orchestrator, SettingsRepository settingsRepository) {
        this.orchestrator = orchestrator;
        this.settingsRepository = settingsRepository;
        this.complianceService = new org.ome.converter.service.validation.OmeZarrComplianceService();

        for (org.ome.converter.service.validation.ComplianceCategory cat : org.ome.converter.service.validation.ComplianceCategory.values()) {
            categoryStatusProperties.put(cat, new SimpleStringProperty("--"));
        }

        UserSettingsEntity settings = settingsRepository.loadSettings();
        if (settings.lastDestinationDirectory() != null) {
            this.targetDestinationPath.set(settings.lastDestinationDirectory());
        } else {
            this.targetDestinationPath.set(System.getProperty("user.home"));
        }
        this.preserveVendorMetadata.set(settings.preserveVendorMetadata());

        AsyncEventBus.getInstance().register(this);
    }

    public void startConversion(Runnable onSuccess, java.util.function.Consumer<Exception> onError) {
        if (sourceFilePath.get().isBlank()) {
            onError.accept(new IllegalArgumentException("Please select a valid source slide file."));
            return;
        }
        if (targetDestinationPath.get().isBlank()) {
            onError.accept(new IllegalArgumentException("Please select a target destination directory on disk."));
            return;
        }

        File srcFile = new File(sourceFilePath.get());
        if (!srcFile.exists() || !srcFile.isFile()) {
            onError.accept(new IllegalArgumentException("Source file does not exist: " + sourceFilePath.get()));
            return;
        }

        String fileName = srcFile.getName().toLowerCase();
        String selectedFormatStr = sourceFormat.get() != null ? sourceFormat.get().toLowerCase() : "";
        boolean isOirMode = selectedFormatStr.contains(".oir");
        boolean isVsiMode = selectedFormatStr.contains(".vsi");

        if (isOirMode && !fileName.endsWith(".oir")) {
            String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "none";
            onError.accept(new IllegalArgumentException(String.format(
                "Source format mismatch: Olympus OIR (.oir) mode is selected, but the provided input file '%s' has extension '%s'. Please select Olympus VSI (.vsi) mode or choose an .oir file.",
                srcFile.getName(), ext
            )));
            return;
        }

        if (isVsiMode && !fileName.endsWith(".vsi")) {
            String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "none";
            onError.accept(new IllegalArgumentException(String.format(
                "Source format mismatch: Olympus VSI (.vsi) mode is selected, but the provided input file '%s' has extension '%s'. Please select Olympus OIR (.oir) mode or choose a .vsi file.",
                srcFile.getName(), ext
            )));
            return;
        }

        File destDir = new File(targetDestinationPath.get());
        if (!destDir.exists() || !destDir.isDirectory()) {
            onError.accept(new IllegalArgumentException("Target destination directory does not exist: " + targetDestinationPath.get()));
            return;
        }

        long freeSpaceBytes = destDir.getUsableSpace();
        if (freeSpaceBytes < 200 * 1024 * 1024L) {
            onError.accept(new IllegalStateException(String.format(
                "Disk Space Warning: Target drive has only %.2f MB free space. Conversion may fail due to limited storage.",
                freeSpaceBytes / (1024.0 * 1024.0)
            )));
            return;
        }

        try {
            currentJobId = "JOB-" + UUID.randomUUID().toString().substring(0, 8);
            Path src = Paths.get(sourceFilePath.get());
            Path dest = Paths.get(targetDestinationPath.get());

            UserSettingsEntity currentSettings = settingsRepository.loadSettings();
            settingsRepository.saveSettings(new UserSettingsEntity(
                src.getParent() != null ? src.getParent().toString() : currentSettings.lastSourceDirectory(),
                dest.toString(),
                currentSettings.defaultTileWidth(),
                currentSettings.defaultTileHeight(),
                currentSettings.defaultCodec(),
                currentSettings.compressionLevel(),
                cpuThreads.get(),
                preserveVendorMetadata.get()
            ));

            ConversionRequest request = new ConversionRequest(
                currentJobId,
                src,
                dest,
                ChunkSpec.defaultSpec(),
                preserveVendorMetadata.get(),
                cpuThreads.get(),
                targetVersion.get()
            );

            resetGapAnalysisState(srcFile.getName(), targetVersion.get().getDisplayName());

            converting.set(true);
            progressPercentage.set(0.0);
            statusText.set("Initializing Conversion Engine (" + targetVersion.get().getDisplayName() + ")...");
            logMessages.add("[SYSTEM] Starting conversion job: " + currentJobId + " using " + targetVersion.get().getDisplayName() + " with " + cpuThreads.get() + " threads");

            orchestrator.submitConversion(request);

        } catch (Exception e) {
            converting.set(false);
            statusText.set("Error: " + e.getMessage());
            onError.accept(e);
        }
    }

    private void resetGapAnalysisState(String fileName, String versionDisplayName) {
        Platform.runLater(() -> {
            dashboardSubtitle.set("Dataset: " + fileName + " | Spec: " + versionDisplayName + " (Analyzing...)");
            totalFields.set("Calculating...");
            mappedFields.set("--");
            vendorDumped.set("--");
            lossFields.set("--");
            badgeMappedText.set("Mapped: --");
            badgeVendorText.set("Vendor Custom (Dumped): --");
            badgeLossText.set("Loss: --");
            badgeAllText.set("All Fields (0)");
            lostHeader.set("Lost Metadata Inventory (Analyzing...)");
            lostItems.clear();
            allItems.clear();
        });
    }

    public void cancelCurrentJob() {
        if (currentJobId != null) {
            orchestrator.cancelJob(currentJobId);
            converting.set(false);
            statusText.set("Conversion Cancelled");
            logMessages.add("[SYSTEM] Cancelled job: " + currentJobId);
        }
    }

    private Path lastReportDirectory;

    public ObservableList<GapAnalysisResult.GapAnalysisItemDetail> getAllItems() {
        return allItems;
    }

    public void updateGapAnalysisResults(GapAnalysisResult result) {
        if (result == null) return;
        if (result.htmlReportPath() != null) {
            this.lastReportDirectory = result.htmlReportPath().getParent();
        }
        Platform.runLater(() -> {
            dashboardSubtitle.set("Dataset: " + result.datasetName() + " | Spec: " + result.targetVersion().getDisplayName());
            totalFields.set(String.valueOf(result.totalOriginalCount()));
            mappedFields.set(String.valueOf(result.mappedCount()));
            vendorDumped.set(String.valueOf(result.vendorDumpedCount()));
            lossFields.set(result.lossCount() + " (Attention)");

            badgeMappedText.set("Mapped: " + result.mappedCount());
            badgeVendorText.set("Vendor Custom (Dumped): " + result.vendorDumpedCount());
            badgeLossText.set("Loss: " + result.lossCount());
            badgeAllText.set("All Fields (" + result.totalOriginalCount() + ")");
            lostHeader.set("Metadata Inventory (Displaying " + result.totalOriginalCount() + " Total Fields)");

            if (result.lostItems() != null) {
                lostItems.setAll(result.lostItems());
            } else {
                lostItems.clear();
            }

            if (result.allItems() != null && !result.allItems().isEmpty()) {
                allItems.setAll(result.allItems());
            } else if (result.lostItems() != null) {
                allItems.setAll(result.lostItems());
            } else {
                allItems.clear();
            }

            if (result.htmlReportPath() != null && result.htmlReportPath().getParent() != null) {
                complianceDatasetPath.set(result.htmlReportPath().getParent().toAbsolutePath().toString());
            }
        });
    }

    public void runComplianceCheck(Runnable onDone, java.util.function.Consumer<Exception> onError) {
        String pathStr = complianceDatasetPath.get();
        if (pathStr == null || pathStr.isBlank()) {
            if (onError != null) onError.accept(new IllegalArgumentException("Please select a target OME-Zarr directory first."));
            return;
        }

        File file = new File(pathStr);
        if (!file.exists() || !file.isDirectory()) {
            if (onError != null) onError.accept(new IllegalArgumentException("Specified OME-Zarr dataset path does not exist or is not a directory: " + pathStr));
            return;
        }

        validatingCompliance.set(true);
        complianceProgressText.set("Validating...");
        complianceOverallStatus.set("VALIDATING...");

        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            return complianceService.validateDataset(file.toPath());
        }).thenAcceptAsync(result -> {
            Platform.runLater(() -> {
                latestComplianceResult.set(result);
                detectedOmeVersion.set(result.detectedOmeVersion());
                detectedZarrVersion.set(result.detectedZarrVersion());
                complianceOverallStatus.set(result.overallStatus().getDisplayName());
                complianceErrorsCount.set(String.valueOf(result.errorCount()));
                complianceWarningsCount.set(String.valueOf(result.warningCount()));
                complianceInfoCount.set(String.valueOf(result.infoCount()));

                for (org.ome.converter.service.validation.ComplianceCategory cat : org.ome.converter.service.validation.ComplianceCategory.values()) {
                    org.ome.converter.service.validation.ComplianceSeverity sev = result.categoryStatuses().getOrDefault(cat, org.ome.converter.service.validation.ComplianceSeverity.INFO);
                    categoryStatusProperties.get(cat).set(sev.name());
                }

                validatingCompliance.set(false);
                complianceProgressText.set("Validation Complete!");
                if (onDone != null) onDone.run();
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                validatingCompliance.set(false);
                complianceProgressText.set("Validation Error");
                complianceOverallStatus.set("ERROR");
                if (onError != null) onError.accept(new Exception(ex));
            });
            return null;
        });
    }

    public void openComplianceReport() {
        org.ome.converter.service.validation.ComplianceResult res = latestComplianceResult.get();
        if (res != null && res.htmlReportPath() != null && Files.exists(res.htmlReportPath())) {
            try {
                Desktop.getDesktop().open(res.htmlReportPath().toFile());
            } catch (Exception e) {
                log.error("Could not open compliance report file: {}", res.htmlReportPath(), e);
            }
        }
    }

    public void openOfficialValidator(String customPath) throws Exception {
        String pathStr = (customPath != null && !customPath.isBlank()) ? customPath : complianceDatasetPath.get();
        if (pathStr == null || pathStr.isBlank()) {
            throw new IllegalArgumentException("No OME-Zarr dataset path selected.");
        }
        Path path = Paths.get(pathStr);
        org.ome.converter.service.runtime.BundledOmeZarrRuntimeService.getInstance().launchOfficialValidator(path);
    }

    public void openReportFile(String filename) {
        File reportFile = null;
        if (lastReportDirectory != null) {
            reportFile = lastReportDirectory.resolve(filename).toFile();
        }
        if (reportFile == null || !reportFile.exists()) {
            String destDirStr = targetDestinationPath.get();
            if (destDirStr != null && !destDirStr.isBlank()) {
                File srcFile = new File(sourceFilePath.get());
                String zarrName = srcFile.getName();
                if (zarrName.contains(".")) {
                    zarrName = zarrName.substring(0, zarrName.lastIndexOf('.'));
                }
                reportFile = new File(new File(destDirStr, zarrName + ".zarr"), filename);
                if (!reportFile.exists()) {
                    reportFile = new File(destDirStr, filename);
                }
            }
        }
        if (reportFile != null && reportFile.exists()) {
            try {
                Desktop.getDesktop().open(reportFile);
            } catch (Exception e) {
                log.error("Could not open report file: {}", reportFile.getAbsolutePath(), e);
            }
        }
    }

    @Override
    public void onProgress(ProgressEvent event) {
        if (event.jobId().equals(currentJobId)) {
            Platform.runLater(() -> {
                progressPercentage.set(event.percentage() / 100.0);
                statusText.set(event.currentTask());

                if (event.currentTask().contains("MB/s")) {
                    int idx = event.currentTask().indexOf('(');
                    if (idx >= 0 && event.currentTask().contains(")")) {
                        throughputText.set(event.currentTask().substring(idx + 1, event.currentTask().indexOf(')')));
                    }
                }

                if (event.completed()) {
                    converting.set(false);
                    statusText.set("Conversion Finished Successfully!");
                } else if (event.failed()) {
                    converting.set(false);
                    statusText.set("Conversion Failed");
                }
            });
        }
    }

    @Override
    public void onLog(LogEvent event) {
        Platform.runLater(() -> {
            String msg = String.format("[%s] [%s] %s", event.timestamp().toString().substring(11, 19), event.level(), event.message());
            logMessages.add(msg);
        });
    }

    @Override
    public void onGapAnalysis(GapAnalysisEvent event) {
        if (event != null && event.result() != null) {
            updateGapAnalysisResults(event.result());
        }
    }

    // Properties Getters
    public StringProperty sourceFormatProperty() { return sourceFormat; }
    public StringProperty sourceFilePathProperty() { return sourceFilePath; }
    public StringProperty targetDestinationPathProperty() { return targetDestinationPath; }
    public DoubleProperty progressPercentageProperty() { return progressPercentage; }
    public StringProperty statusTextProperty() { return statusText; }
    public StringProperty throughputTextProperty() { return throughputText; }
    public BooleanProperty convertingProperty() { return converting; }
    public BooleanProperty preserveVendorMetadataProperty() { return preserveVendorMetadata; }
    public IntegerProperty cpuThreadsProperty() { return cpuThreads; }
    public ObjectProperty<org.ome.converter.core.model.OmeZarrVersion> targetVersionProperty() { return targetVersion; }

    public StringProperty dashboardSubtitleProperty() { return dashboardSubtitle; }
    public StringProperty totalFieldsProperty() { return totalFields; }
    public StringProperty mappedFieldsProperty() { return mappedFields; }
    public StringProperty vendorDumpedProperty() { return vendorDumped; }
    public StringProperty lossFieldsProperty() { return lossFields; }
    public StringProperty badgeMappedTextProperty() { return badgeMappedText; }
    public StringProperty badgeVendorTextProperty() { return badgeVendorText; }
    public StringProperty badgeLossTextProperty() { return badgeLossText; }
    public StringProperty badgeAllTextProperty() { return badgeAllText; }
    public StringProperty lostHeaderProperty() { return lostHeader; }

    public StringProperty complianceDatasetPathProperty() { return complianceDatasetPath; }
    public StringProperty detectedOmeVersionProperty() { return detectedOmeVersion; }
    public StringProperty detectedZarrVersionProperty() { return detectedZarrVersion; }
    public StringProperty complianceOverallStatusProperty() { return complianceOverallStatus; }
    public StringProperty complianceErrorsCountProperty() { return complianceErrorsCount; }
    public StringProperty complianceWarningsCountProperty() { return complianceWarningsCount; }
    public StringProperty complianceInfoCountProperty() { return complianceInfoCount; }
    public StringProperty complianceProgressTextProperty() { return complianceProgressText; }
    public BooleanProperty validatingComplianceProperty() { return validatingCompliance; }
    public ObjectProperty<org.ome.converter.service.validation.ComplianceResult> latestComplianceResultProperty() { return latestComplianceResult; }
    public java.util.Map<org.ome.converter.service.validation.ComplianceCategory, StringProperty> categoryStatusProperties() { return categoryStatusProperties; }

    public ObservableList<GapAnalysisResult.GapAnalysisItemDetail> getLostItems() { return lostItems; }
    public ObservableList<String> getLogMessages() { return logMessages; }
    public ObservableList<JobEntity> getJobHistory() { return jobHistory; }
}

