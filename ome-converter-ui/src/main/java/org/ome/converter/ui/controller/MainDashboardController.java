package org.ome.converter.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
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
import java.util.List;

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

    @FXML private ToggleButton btnCategoryLoss;
    @FXML private ToggleButton btnCategoryMapped;
    @FXML private ToggleButton btnCategoryVendor;
    @FXML private ToggleButton btnCategoryAll;

    @FXML private Label lblLostHeader;
    @FXML private TextField txtLostSearch;
    @FXML private TableView<GapAnalysisResult.GapAnalysisItemDetail> tblLostMetadata;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colOriginalKey;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colOriginalValue;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colStatus;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colExplanation;

    @FXML private ListView<String> lstLogs;

    // Compliance UI Controls
    @FXML private TextField txtComplianceDatasetPath;
    @FXML private Button btnCheckCompliance;
    @FXML private Label lblComplianceOmeVersion;
    @FXML private Label lblComplianceZarrVersion;
    @FXML private Label lblComplianceProgress;
    @FXML private Label lblComplianceOverallStatus;
    @FXML private Label lblComplianceErrors;
    @FXML private Label lblComplianceWarnings;
    @FXML private Label lblComplianceInfo;
    @FXML private Button btnOpenComplianceReport;
    @FXML private Button btnOpenOfficialValidator;

    @FXML private Label lblStatusStructure;
    @FXML private Label lblStatusVersion;
    @FXML private Label lblStatusMultiscales;
    @FXML private Label lblStatusAxes;
    @FXML private Label lblStatusTransformations;
    @FXML private Label lblStatusOmero;
    @FXML private Label lblStatusLabels;
    @FXML private Label lblStatusPlateWell;

    private MainDashboardViewModel viewModel;
    private final ObservableList<GapAnalysisResult.GapAnalysisItemDetail> displayedTableItems = FXCollections.observableArrayList();

    private final ToggleGroup formatToggleGroup = new ToggleGroup();
    private final ToggleGroup versionToggleGroup = new ToggleGroup();

    private String activeCategory = "LOSS";
    private boolean isUpdatingSelection = false;

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
        btnVersionV04.setToggleGroup(versionToggleGroup);
        btnVersionV05.setToggleGroup(versionToggleGroup);
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

        // Bind Gap Analysis Button Text Properties
        if (btnCategoryMapped != null) btnCategoryMapped.textProperty().bind(viewModel.badgeMappedTextProperty());
        if (btnCategoryVendor != null) btnCategoryVendor.textProperty().bind(viewModel.badgeVendorTextProperty());
        if (btnCategoryLoss != null) btnCategoryLoss.textProperty().bind(viewModel.badgeLossTextProperty());
        if (btnCategoryAll != null) btnCategoryAll.textProperty().bind(viewModel.badgeAllTextProperty());

        lblLostHeader.textProperty().bind(viewModel.lostHeaderProperty());

        // Table Setup with safe lambdas for JavaFX record properties
        colOriginalKey.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().originalKey() : ""));
        colOriginalValue.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().originalValue() : ""));
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().status() : ""));
        colExplanation.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().explanation() : ""));

        tblLostMetadata.setItems(displayedTableItems);

        txtLostSearch.textProperty().addListener((obs, oldVal, newVal) -> updateTableFilter());

        viewModel.getLostItems().addListener((ListChangeListener<GapAnalysisResult.GapAnalysisItemDetail>) change -> {
            Platform.runLater(this::updateTableFilter);
        });

        viewModel.getAllItems().addListener((ListChangeListener<GapAnalysisResult.GapAnalysisItemDetail>) change -> {
            Platform.runLater(() -> {
                activeCategory = "LOSS";
                updateCategoryButtonSelection();
                updateTableFilter();
            });
        });

        btnConvert.disableProperty().bind(viewModel.convertingProperty());
        btnCancel.disableProperty().bind(viewModel.convertingProperty().not());

        lstLogs.setItems(viewModel.getLogMessages());
        updateCategoryButtonSelection();

        // Bind Compliance UI Controls
        txtComplianceDatasetPath.textProperty().bindBidirectional(viewModel.complianceDatasetPathProperty());
        lblComplianceOmeVersion.textProperty().bind(viewModel.detectedOmeVersionProperty());
        lblComplianceZarrVersion.textProperty().bind(viewModel.detectedZarrVersionProperty());
        lblComplianceProgress.textProperty().bind(viewModel.complianceProgressTextProperty());
        lblComplianceOverallStatus.textProperty().bind(viewModel.complianceOverallStatusProperty());
        lblComplianceErrors.textProperty().bind(viewModel.complianceErrorsCountProperty());
        lblComplianceWarnings.textProperty().bind(viewModel.complianceWarningsCountProperty());
        lblComplianceInfo.textProperty().bind(viewModel.complianceInfoCountProperty());

        btnCheckCompliance.disableProperty().bind(viewModel.validatingComplianceProperty());

        viewModel.latestComplianceResultProperty().addListener((obs, oldRes, newRes) -> {
            boolean hasReport = newRes != null && newRes.htmlReportPath() != null && newRes.htmlReportPath().toFile().exists();
            btnOpenComplianceReport.setDisable(!hasReport);
        });

        var catProps = viewModel.categoryStatusProperties();
        if (lblStatusStructure != null) lblStatusStructure.textProperty().bind(catProps.get(org.ome.converter.service.validation.ComplianceCategory.STRUCTURE));
        if (lblStatusVersion != null) lblStatusVersion.textProperty().bind(catProps.get(org.ome.converter.service.validation.ComplianceCategory.VERSION));
        if (lblStatusMultiscales != null) lblStatusMultiscales.textProperty().bind(catProps.get(org.ome.converter.service.validation.ComplianceCategory.MULTISCALES));
        if (lblStatusAxes != null) lblStatusAxes.textProperty().bind(catProps.get(org.ome.converter.service.validation.ComplianceCategory.AXES));
        if (lblStatusTransformations != null) lblStatusTransformations.textProperty().bind(catProps.get(org.ome.converter.service.validation.ComplianceCategory.TRANSFORMATIONS));
        if (lblStatusOmero != null) lblStatusOmero.textProperty().bind(catProps.get(org.ome.converter.service.validation.ComplianceCategory.OMERO));
        if (lblStatusLabels != null) lblStatusLabels.textProperty().bind(catProps.get(org.ome.converter.service.validation.ComplianceCategory.LABELS));
        if (lblStatusPlateWell != null) lblStatusPlateWell.textProperty().bind(catProps.get(org.ome.converter.service.validation.ComplianceCategory.PLATE_WELL));
    }

    @FXML
    private void handleCategoryLoss() {
        if (isUpdatingSelection) return;
        activeCategory = "LOSS";
        updateCategoryButtonSelection();
        updateTableFilter();
    }

    @FXML
    private void handleCategoryMapped() {
        if (isUpdatingSelection) return;
        activeCategory = "MAPPED";
        updateCategoryButtonSelection();
        updateTableFilter();
    }

    @FXML
    private void handleCategoryVendor() {
        if (isUpdatingSelection) return;
        activeCategory = "VENDOR";
        updateCategoryButtonSelection();
        updateTableFilter();
    }

    @FXML
    private void handleCategoryAll() {
        if (isUpdatingSelection) return;
        activeCategory = "ALL";
        updateCategoryButtonSelection();
        updateTableFilter();
    }

    private void updateCategoryButtonSelection() {
        if (isUpdatingSelection) return;
        isUpdatingSelection = true;
        try {
            if (btnCategoryLoss != null) btnCategoryLoss.setSelected("LOSS".equals(activeCategory));
            if (btnCategoryMapped != null) btnCategoryMapped.setSelected("MAPPED".equals(activeCategory));
            if (btnCategoryVendor != null) btnCategoryVendor.setSelected("VENDOR".equals(activeCategory));
            if (btnCategoryAll != null) btnCategoryAll.setSelected("ALL".equals(activeCategory));
        } finally {
            isUpdatingSelection = false;
        }
    }

    private void updateTableFilter() {
        ObservableList<GapAnalysisResult.GapAnalysisItemDetail> activeSource =
            (!viewModel.getAllItems().isEmpty()) ? viewModel.getAllItems() : viewModel.getLostItems();

        String search = txtLostSearch.getText();
        String searchFilter = (search != null) ? search.trim().toLowerCase() : "";

        List<GapAnalysisResult.GapAnalysisItemDetail> filtered = activeSource.stream().filter(item -> {
            if (item == null) return false;

            // Category Filter
            boolean matchesCategory;
            String statusUpper = item.status() != null ? item.status().toUpperCase() : "";

            switch (activeCategory) {
                case "MAPPED":
                    matchesCategory = statusUpper.contains("MAPPED") && !statusUpper.contains("UNMAPPED");
                    break;
                case "VENDOR":
                    matchesCategory = statusUpper.contains("VENDOR") || statusUpper.contains("STRUCTURAL");
                    break;
                case "LOSS":
                    matchesCategory = statusUpper.contains("LOSS") || statusUpper.contains("MISSING") || statusUpper.contains("UNMAPPED") || statusUpper.contains("UNREGISTERED");
                    break;
                case "ALL":
                default:
                    matchesCategory = true;
                    break;
            }

            if (!matchesCategory) return false;

            // Text Search Filter
            if (searchFilter.isEmpty()) return true;
            return (item.originalKey() != null && item.originalKey().toLowerCase().contains(searchFilter))
                || (item.originalValue() != null && item.originalValue().toLowerCase().contains(searchFilter))
                || (item.explanation() != null && item.explanation().toLowerCase().contains(searchFilter));
        }).toList();

        displayedTableItems.setAll(filtered);
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

    @FXML
    private void handleBrowseComplianceDataset() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select OME-Zarr Output Directory to Validate");

        if (txtComplianceDatasetPath != null && !txtComplianceDatasetPath.getText().isBlank()) {
            File existing = new File(txtComplianceDatasetPath.getText());
            if (existing.exists() && existing.isDirectory()) {
                dirChooser.setInitialDirectory(existing);
            }
        }

        Stage stage = (Stage) txtComplianceDatasetPath.getScene().getWindow();
        File selectedDir = dirChooser.showDialog(stage);
        if (selectedDir != null) {
            viewModel.complianceDatasetPathProperty().set(selectedDir.getAbsolutePath());
        }
    }

    @FXML
    private void handleCheckCompliance() {
        viewModel.runComplianceCheck(
            () -> {},
            (ex) -> {
                AlertHelper.showInputValidationError("Compliance Validation Error", ex.getMessage());
            }
        );
    }

    @FXML
    private void handleOpenComplianceReport() {
        viewModel.openComplianceReport();
    }

    @FXML
    private void handleOpenOfficialValidator() {
        try {
            viewModel.openOfficialValidator(null);
        } catch (Exception e) {
            AlertHelper.showInputValidationError("Official OME-NGFF Validator Error", e.getMessage());
        }
    }
}
