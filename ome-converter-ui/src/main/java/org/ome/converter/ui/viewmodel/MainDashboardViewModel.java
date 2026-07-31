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
    private final StringProperty lostHeader = new SimpleStringProperty("Lost Metadata Inventory (Displaying 0 Lost / Missing Fields)");

    private final ObservableList<GapAnalysisResult.GapAnalysisItemDetail> lostItems = FXCollections.observableArrayList();
    private final ObservableList<String> logMessages = FXCollections.observableArrayList();
    private final ObservableList<JobEntity> jobHistory = FXCollections.observableArrayList();

    private final ConversionOrchestrator orchestrator;
    private final SettingsRepository settingsRepository;
    private String currentJobId;

    public MainDashboardViewModel() {
        this(new ConversionOrchestrator(), new JsonFileSettingsRepository());
    }

    public MainDashboardViewModel(ConversionOrchestrator orchestrator, SettingsRepository settingsRepository) {
        this.orchestrator = orchestrator;
        this.settingsRepository = settingsRepository;

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
            lostHeader.set("Lost Metadata Inventory (Analyzing...)");
            lostItems.clear();
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

    public void updateGapAnalysisResults(GapAnalysisResult result) {
        if (result == null) return;
        Platform.runLater(() -> {
            dashboardSubtitle.set("Dataset: " + result.datasetName() + " | Spec: " + result.targetVersion().getDisplayName());
            totalFields.set(String.valueOf(result.totalOriginalCount()));
            mappedFields.set(String.valueOf(result.mappedCount()));
            vendorDumped.set(String.valueOf(result.vendorDumpedCount()));
            lossFields.set(result.lossCount() + " (Attention)");

            badgeMappedText.set("Mapped: " + result.mappedCount());
            badgeVendorText.set("Vendor Custom (Dumped): " + result.vendorDumpedCount());
            badgeLossText.set("Loss: " + result.lossCount());
            lostHeader.set("Lost Metadata Inventory (Displaying " + result.lostItems().size() + " Lost / Missing Fields)");

            lostItems.clear();
            if (result.lostItems() != null) {
                lostItems.addAll(result.lostItems());
            }
        });
    }

    public void openReportFile(String filename) {
        String destDirStr = targetDestinationPath.get();
        if (destDirStr != null && !destDirStr.isBlank()) {
            File srcFile = new File(sourceFilePath.get());
            String zarrName = srcFile.getName();
            if (zarrName.contains(".")) {
                zarrName = zarrName.substring(0, zarrName.lastIndexOf('.'));
            }
            File reportFile = new File(new File(destDirStr, zarrName + ".zarr"), filename);
            if (!reportFile.exists()) {
                reportFile = new File(destDirStr, filename);
            }
            if (reportFile.exists()) {
                try {
                    Desktop.getDesktop().open(reportFile);
                } catch (Exception e) {
                    log.error("Could not open report file: {}", reportFile.getAbsolutePath(), e);
                }
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
    public StringProperty lostHeaderProperty() { return lostHeader; }

    public ObservableList<GapAnalysisResult.GapAnalysisItemDetail> getLostItems() { return lostItems; }
    public ObservableList<String> getLogMessages() { return logMessages; }
    public ObservableList<JobEntity> getJobHistory() { return jobHistory; }
}
