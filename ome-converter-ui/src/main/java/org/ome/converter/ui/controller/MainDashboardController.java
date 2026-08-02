package org.ome.converter.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.ome.converter.core.model.GapAnalysisResult;
import org.ome.converter.core.model.OmeZarrVersion;
import org.ome.converter.ui.util.AlertHelper;
import org.ome.converter.ui.viewmodel.MainDashboardViewModel;

import java.awt.Desktop;
import java.io.File;

public class MainDashboardController {

    // Toggle Buttons for Source Image Format
    @FXML private ToggleButton btnFormatVsi;
    @FXML private ToggleButton btnFormatOir;

    // Toggle Buttons for OME-Zarr Target Version
    @FXML private ToggleButton btnVersionV05;
    @FXML private ToggleButton btnVersionV04;

    @FXML private TextField txtSourceFile;
    @FXML private TextField txtTargetDestination;
    @FXML private CheckBox chkVendorMetadata;

    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private Label lblThroughput;
    @FXML private Button btnConvert;
    @FXML private Button btnCancel;

    // Gap Analysis UI Controls
    @FXML private Label lblDashboardSubtitle;
    @FXML private Label lblTotalFields;
    @FXML private Label lblMappedFields;
    @FXML private Label lblVendorDumped;
    @FXML private Label lblLossFields;

    @FXML private Label lblBadgeMapped;
    @FXML private Label lblBadgeVendor;
    @FXML private Label lblBadgeLoss;

    @FXML private Label lblLostHeader;
    @FXML private TextField txtLostSearch;
    @FXML private TableView<GapAnalysisResult.GapAnalysisItemDetail> tblLostMetadata;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colOriginalKey;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colOriginalValue;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colStatus;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colExplanation;

    @FXML private ListView<String> lstLogs;

    private MainDashboardViewModel viewModel;
    private FilteredList<GapAnalysisResult.GapAnalysisItemDetail> filteredLostItems;

    private final ToggleGroup formatToggleGroup = new ToggleGroup();
    private final ToggleGroup versionToggleGroup = new ToggleGroup();

    @FXML
    public void initialize() {
        viewModel = new MainDashboardViewModel();

        // Format Toggle Group Setup
        btnFormatVsi.setToggleGroup(formatToggleGroup);
        btnFormatOir.setToggleGroup(formatToggleGroup);
        btnFormatVsi.setSelected(true);

        formatToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == btnFormatOir) {
                viewModel.sourceFormatProperty().set("Olympus FluoView OIR (.oir)");
            } else {
                btnFormatVsi.setSelected(true);
                viewModel.sourceFormatProperty().set("Olympus CellSens VSI (.vsi)");
            }
            checkAndShowFormatMismatchWarning();
        });

        // Version Toggle Group Setup
        btnVersionV05.setToggleGroup(versionToggleGroup);
        btnVersionV04.setToggleGroup(versionToggleGroup);
        btnVersionV05.setSelected(true);

        versionToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == btnVersionV04) {
                viewModel.targetVersionProperty().set(OmeZarrVersion.OME_ZARR_0_4);
            } else {
                btnVersionV05.setSelected(true);
                viewModel.targetVersionProperty().set(OmeZarrVersion.OME_ZARR_0_5);
            }
        });

        txtSourceFile.textProperty().bindBidirectional(viewModel.sourceFilePathProperty());
        txtTargetDestination.textProperty().bindBidirectional(viewModel.targetDestinationPathProperty());
        chkVendorMetadata.selectedProperty().bindBidirectional(viewModel.preserveVendorMetadataProperty());

        progressBar.progressProperty().bind(viewModel.progressPercentageProperty());
        lblStatus.textProperty().bind(viewModel.statusTextProperty());
        lblThroughput.textProperty().bind(viewModel.throughputTextProperty());

        // Bind Gap Analysis Numerical Properties
        lblDashboardSubtitle.textProperty().bind(viewModel.dashboardSubtitleProperty());
        lblTotalFields.textProperty().bind(viewModel.totalFieldsProperty());
        lblMappedFields.textProperty().bind(viewModel.mappedFieldsProperty());
        lblVendorDumped.textProperty().bind(viewModel.vendorDumpedProperty());
        lblLossFields.textProperty().bind(viewModel.lossFieldsProperty());

        lblBadgeMapped.textProperty().bind(viewModel.badgeMappedTextProperty());
        lblBadgeVendor.textProperty().bind(viewModel.badgeVendorTextProperty());
        lblBadgeLoss.textProperty().bind(viewModel.badgeLossTextProperty());
        lblLostHeader.textProperty().bind(viewModel.lostHeaderProperty());

        // Table Setup with safe lambdas for JavaFX record properties
        colOriginalKey.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().originalKey() : ""));
        colOriginalValue.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().originalValue() : ""));
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().status() : ""));
        colExplanation.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().explanation() : ""));

        filteredLostItems = new FilteredList<>(viewModel.getLostItems(), p -> true);
        tblLostMetadata.setItems(filteredLostItems);

        viewModel.getLostItems().addListener((ListChangeListener<GapAnalysisResult.GapAnalysisItemDetail>) change -> {
            Platform.runLater(() -> tblLostMetadata.refresh());
        });

        viewModel.dashboardSubtitleProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> tblLostMetadata.refresh());
        });

        txtLostSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredLostItems.setPredicate(item -> {
                if (newVal == null || newVal.isBlank()) return true;
                String filter = newVal.toLowerCase();
                return (item.originalKey() != null && item.originalKey().toLowerCase().contains(filter))
                    || (item.originalValue() != null && item.originalValue().toLowerCase().contains(filter))
                    || (item.explanation() != null && item.explanation().toLowerCase().contains(filter));
            });
            tblLostMetadata.refresh();
        });

        btnConvert.disableProperty().bind(viewModel.convertingProperty());
        btnCancel.disableProperty().bind(viewModel.convertingProperty().not());

        lstLogs.setItems(viewModel.getLogMessages());
    }

    @FXML
    private void handleBrowseSourceFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Microscopic Image Slide File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Olympus Microscopic Slides (*.vsi, *.oir)", "*.vsi", "*.oir"),
            new FileChooser.ExtensionFilter("Olympus VSI (*.vsi)", "*.vsi"),
            new FileChooser.ExtensionFilter("Olympus OIR (*.oir)", "*.oir")
        );

        if (!txtSourceFile.getText().isBlank()) {
            File existing = new File(txtSourceFile.getText());
            if (existing.getParentFile() != null && existing.getParentFile().exists()) {
                fileChooser.setInitialDirectory(existing.getParentFile());
            }
        }

        Stage stage = (Stage) txtSourceFile.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            viewModel.sourceFilePathProperty().set(selectedFile.getAbsolutePath());
            checkAndShowFormatMismatchWarning();
        }
    }

    private void checkAndShowFormatMismatchWarning() {
        String currentPath = txtSourceFile.getText();
        if (currentPath == null || currentPath.isBlank()) return;

        File file = new File(currentPath);
        String name = file.getName().toLowerCase();

        boolean isOirMode = btnFormatOir.isSelected();
        boolean isVsiMode = btnFormatVsi.isSelected();

        if (isVsiMode && name.endsWith(".oir")) {
            AlertHelper.showFormatMismatchError(
                "Source Format Mismatch",
                "Format Selection Mismatch Warning",
                String.format(
                    "You have selected 'Olympus VSI (.vsi)' mode, but the chosen file '%s' is an Olympus OIR (.oir) file.\n\nPlease select a .vsi file, or switch the mode to 'Olympus OIR (.oir)'.",
                    file.getName()
                )
            );
        } else if (isOirMode && name.endsWith(".vsi")) {
            AlertHelper.showFormatMismatchError(
                "Source Format Mismatch",
                "Format Selection Mismatch Warning",
                String.format(
                    "You have selected 'Olympus OIR (.oir)' mode, but the chosen file '%s' is an Olympus VSI (.vsi) file.\n\nPlease select an .oir file, or switch the mode to 'Olympus VSI (.vsi)'.",
                    file.getName()
                )
            );
        }
    }

    @FXML
    private void handleBrowseTargetDestination() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Destination Directory for OME-Zarr Output");

        if (!txtTargetDestination.getText().isBlank()) {
            File existing = new File(txtTargetDestination.getText());
            if (existing.exists() && existing.isDirectory()) {
                dirChooser.setInitialDirectory(existing);
            }
        }

        Stage stage = (Stage) txtTargetDestination.getScene().getWindow();
        File selectedDir = dirChooser.showDialog(stage);
        if (selectedDir != null) {
            viewModel.targetDestinationPathProperty().set(selectedDir.getAbsolutePath());
        }
    }

    @FXML
    private void handleStartConversion() {
        viewModel.startConversion(
            () -> {
                String targetPath = txtTargetDestination.getText();
                AlertHelper.showCompletionSuccessWithReport("CONVERTED", targetPath, null);
            },
            (ex) -> {
                if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("disk space")) {
                    AlertHelper.showStorageError("Disk Space Warning", ex.getMessage());
                } else if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("source file")) {
                    AlertHelper.showInputValidationError("Source File Error", ex.getMessage());
                } else {
                    AlertHelper.showConversionError("Conversion Process Error", ex.getMessage(), ex.getCause());
                }
            }
        );
    }

    @FXML
    private void handleCancelConversion() {
        viewModel.cancelCurrentJob();
    }

    @FXML
    private void handleOpenOutputFolder() {
        String targetDirStr = txtTargetDestination.getText();
        if (targetDirStr != null && !targetDirStr.isBlank()) {
            try {
                File dir = new File(targetDirStr);
                if (dir.exists()) {
                    Desktop.getDesktop().open(dir.isDirectory() ? dir : dir.getParentFile());
                } else {
                    AlertHelper.showStorageError("Directory Not Found", "The specified target directory does not exist yet.");
                }
            } catch (Exception e) {
                AlertHelper.showStorageError("Error Opening Directory", e.getMessage());
            }
        } else {
            AlertHelper.showStorageError("No Directory Selected", "Please select a target directory first.");
        }
    }

    @FXML
    private void handleClearLogs() {
        viewModel.getLogMessages().clear();
    }
}
