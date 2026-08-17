package org.ome.converter.ui.controller;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.ome.converter.core.model.GapAnalysisResult;
import org.ome.converter.core.model.OmeZarrVersion;
import org.ome.converter.ui.util.AlertHelper;
import org.ome.converter.ui.viewmodel.MainDashboardViewModel;

import java.awt.Desktop;
import java.io.File;
import java.util.List;

public class MainDashboardController {

    @FXML private VBox cardLeftPanel;

    // Toggle Buttons for Source Image Format
    @FXML private ToggleButton btnFormatVsi;
    @FXML private ToggleButton btnFormatOir;

    // Toggle Buttons for OME-Zarr Target Version
    @FXML private ToggleButton btnVersionV05;
    @FXML private ToggleButton btnVersionV04;

    @FXML private TextField txtSourceFile;
    @FXML private TextField txtTargetDestination;

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
    @FXML private TableView<GapAnalysisResult.GapAnalysisItemDetail> tblLostMetadata;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colOriginalKey;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colOriginalValue;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colConvertedLocation;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colConvertedValue;
    @FXML private TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> colExplanation;

    @FXML private ListView<String> lstLogs;

    @FXML private TabPane mainTabPane;
    @FXML private Tab tabConsole;
    @FXML private Tab tabOfficialValidator;

    // Official Validator UI Controls
    @FXML private TextField txtComplianceDatasetPath;
    @FXML private Button btnCheckCompliance;
    @FXML private javafx.scene.web.WebView webValidatorView;

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
        btnFormatOir.setToggleGroup(formatToggleGroup);
        btnFormatVsi.setToggleGroup(formatToggleGroup);
        btnFormatOir.setSelected(true);
        viewModel.sourceFormatProperty().set("Olympus FluoView OIR (.oir)");

        formatToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == btnFormatVsi) {
                viewModel.sourceFormatProperty().set("Olympus CellSens VSI (.vsi)");
            } else {
                btnFormatOir.setSelected(true);
                viewModel.sourceFormatProperty().set("Olympus FluoView OIR (.oir)");
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

        // Table Setup with wrapping Text nodes and hover Tooltips
        colOriginalKey.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().originalKey() : ""));
        colOriginalValue.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().originalValue() : ""));
        colConvertedLocation.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().convertedLocation() : ""));
        colConvertedValue.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().convertedValue() : ""));
        colExplanation.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue() != null ? cellData.getValue().explanation() : ""));

        setupWrappingCellFactory(colOriginalKey);
        setupWrappingCellFactory(colOriginalValue);
        setupWrappingCellFactory(colConvertedLocation);
        setupWrappingCellFactory(colConvertedValue);
        setupWrappingCellFactory(colExplanation);

        tblLostMetadata.setItems(displayedTableItems);

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

        // Cancel / Reset Button State Handling
        viewModel.convertingProperty().addListener((obs, oldVal, isConverting) -> {
            if (isConverting) {
                btnCancel.setText("Cancel");
            } else {
                btnCancel.setText("Reset");
            }
        });
        btnCancel.setText(viewModel.convertingProperty().get() ? "Cancel" : "Reset");

        lstLogs.setItems(viewModel.getLogMessages());
        updateCategoryButtonSelection();

        // Bind Compliance UI Controls
        if (txtComplianceDatasetPath != null) {
            txtComplianceDatasetPath.textProperty().bindBidirectional(viewModel.complianceDatasetPathProperty());
            viewModel.complianceDatasetPathProperty().addListener((obs, oldPath, newPath) -> {
                if (newPath != null && !newPath.isBlank()) {
                    handleCheckCompliance();
                }
            });
        }

        if (webValidatorView != null) {
            webValidatorView.getEngine().setJavaScriptEnabled(true);
            webValidatorView.getEngine().setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

            webValidatorView.setContextMenuEnabled(false);
            ContextMenu contextMenu = new ContextMenu();
            MenuItem viewSourceItem = new MenuItem("📄 View Page Source Code in Notepad");
            viewSourceItem.setOnAction(e -> openValidationSourceInNotepad());
            contextMenu.getItems().add(viewSourceItem);

            webValidatorView.setOnContextMenuRequested(e -> {
                contextMenu.show(webValidatorView, e.getScreenX(), e.getScreenY());
            });
        }

        setupPathTooltipsAndDragDrop();
    }

    private void setupPathTooltipsAndDragDrop() {
        bindPathTooltip(txtSourceFile, "Source Image File: ");
        bindPathTooltip(txtTargetDestination, "Output Destination Directory: ");
        bindPathTooltip(txtComplianceDatasetPath, "Validator Dataset Directory: ");

        setupSourceFileDragAndDrop(txtSourceFile);
        setupSourceFileDragAndDrop(cardLeftPanel);
        setupTargetDirDragAndDrop(txtTargetDestination);
        setupComplianceDatasetDragAndDrop(txtComplianceDatasetPath);
    }

    private void bindPathTooltip(TextField textField, String prefix) {
        if (textField == null) return;
        Tooltip tooltip = new Tooltip();
        tooltip.setShowDelay(Duration.millis(150));
        tooltip.setShowDuration(Duration.minutes(5));
        tooltip.textProperty().bind(
            Bindings.createStringBinding(
                () -> {
                    String val = textField.getText();
                    return (val != null && !val.isBlank()) ? prefix + val : "No path selected (Hover or Drop file here)";
                },
                textField.textProperty()
            )
        );
        Tooltip.install(textField, tooltip);

        textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                Platform.runLater(textField::selectAll);
            }
        });
    }

    private void setupSourceFileDragAndDrop(javafx.scene.Node node) {
        if (node == null) return;
        node.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                List<File> files = event.getDragboard().getFiles();
                boolean valid = files.stream().anyMatch(f -> {
                    String name = f.getName().toLowerCase();
                    return name.endsWith(".vsi") || name.endsWith(".oir");
                });
                if (valid) {
                    event.acceptTransferModes(TransferMode.COPY);
                }
            }
            event.consume();
        });

        node.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".vsi")) {
                        btnFormatVsi.setSelected(true);
                        viewModel.sourceFormatProperty().set("Olympus CellSens VSI (.vsi)");
                        txtSourceFile.setText(file.getAbsolutePath());
                        checkAndShowFormatMismatchWarning();
                        viewModel.getLogMessages().add("[DRAG&DROP] Selected Olympus VSI image file: " + file.getAbsolutePath());
                        success = true;
                        break;
                    } else if (name.endsWith(".oir")) {
                        btnFormatOir.setSelected(true);
                        viewModel.sourceFormatProperty().set("Olympus FluoView OIR (.oir)");
                        txtSourceFile.setText(file.getAbsolutePath());
                        checkAndShowFormatMismatchWarning();
                        viewModel.getLogMessages().add("[DRAG&DROP] Selected Olympus OIR image file: " + file.getAbsolutePath());
                        success = true;
                        break;
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void setupTargetDirDragAndDrop(Control node) {
        if (node == null) return;
        node.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        node.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                File dropped = db.getFiles().get(0);
                String dirPath = dropped.isDirectory() ? dropped.getAbsolutePath() : (dropped.getParentFile() != null ? dropped.getParentFile().getAbsolutePath() : dropped.getAbsolutePath());
                txtTargetDestination.setText(dirPath);
                viewModel.getLogMessages().add("[DRAG&DROP] Selected target destination directory: " + dirPath);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void setupComplianceDatasetDragAndDrop(Control node) {
        if (node == null) return;
        node.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        node.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                File dropped = db.getFiles().get(0);
                java.nio.file.Path resolved = org.ome.converter.service.runtime.BundledOmeZarrRuntimeService.getInstance().resolveDatasetPath(dropped.toPath());
                txtComplianceDatasetPath.setText(resolved.toAbsolutePath().toString());
                viewModel.getLogMessages().add("[DRAG&DROP] Selected dataset for compliance validation: " + resolved.toAbsolutePath());
                handleCheckCompliance();
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
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

        List<GapAnalysisResult.GapAnalysisItemDetail> filtered = activeSource.stream().filter(item -> {
            if (item == null) return false;

            String statusUpper = item.status() != null ? item.status().toUpperCase() : "";

            switch (activeCategory) {
                case "MAPPED":
                    return statusUpper.contains("MAPPED") && !statusUpper.contains("UNMAPPED");
                case "VENDOR":
                    return statusUpper.contains("VENDOR") || statusUpper.contains("STRUCTURAL");
                case "LOSS":
                    return statusUpper.contains("LOSS") || statusUpper.contains("MISSING") || statusUpper.contains("UNMAPPED") || statusUpper.contains("UNREGISTERED");
                case "ALL":
                default:
                    return true;
            }
        }).toList();

        displayedTableItems.setAll(filtered);
    }

    @FXML
    private void handleBrowseSourceFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files (*.vsi, *.oir)", "*.vsi", "*.oir"),
            new FileChooser.ExtensionFilter("VSI Files (*.vsi)", "*.vsi"),
            new FileChooser.ExtensionFilter("OIR Files (*.oir)", "*.oir")
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
        if (mainTabPane != null && tabConsole != null) {
            mainTabPane.getSelectionModel().select(tabConsole);
        }
        viewModel.startConversion(
            () -> {
                String targetPath = txtTargetDestination.getText();
                AlertHelper.showCompletionSuccess("CONVERTED", targetPath);
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
        if (viewModel.convertingProperty().get()) {
            viewModel.cancelCurrentJob();
        } else {
            // Reset state for new job
            viewModel.progressPercentageProperty().set(0.0);
            viewModel.statusTextProperty().set("Ready");
            viewModel.throughputTextProperty().set("0.00 MB/s");
        }
    }

    private void setupWrappingCellFactory(TableColumn<GapAnalysisResult.GapAnalysisItemDetail, String> col) {
        if (col == null) return;
        col.setCellFactory(tc -> new TableCell<>() {
            private final javafx.scene.text.Text textNode = new javafx.scene.text.Text();
            {
                textNode.wrappingWidthProperty().bind(tc.widthProperty().subtract(16));
                textNode.setStyle("-fx-fill: #334155; -fx-font-size: 12px;");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    setTooltip(null);
                } else {
                    textNode.setText(item);
                    setGraphic(textNode);
                    setText(null);
                    Tooltip tooltip = new Tooltip(item);
                    tooltip.setWrapText(true);
                    tooltip.setMaxWidth(500);
                    tooltip.setShowDelay(Duration.millis(150));
                    setTooltip(tooltip);
                }
            }
        });
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
    private void handleExportGapReport() {
        GapAnalysisResult result = viewModel.getLatestGapAnalysisResult();
        if (result == null && (viewModel.getLostItems().isEmpty() && viewModel.getAllItems().isEmpty())) {
            AlertHelper.showInputValidationError("No Gap Analysis Data", "Please run a conversion first to generate metadata gap analysis data.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Metadata Gap Analysis HTML Report");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML Files (*.html)", "*.html"));

        String datasetName = (result != null && result.datasetName() != null) ? result.datasetName() : "dataset";
        if (datasetName.contains(".")) {
            datasetName = datasetName.substring(0, datasetName.lastIndexOf('.'));
        }
        fileChooser.setInitialFileName(datasetName + "_gap_analysis_report.html");

        String targetDir = txtTargetDestination.getText();
        if (targetDir != null && !targetDir.isBlank()) {
            File existing = new File(targetDir);
            if (existing.exists() && existing.isDirectory()) {
                fileChooser.setInitialDirectory(existing);
            }
        }

        Stage stage = (Stage) mainTabPane.getScene().getWindow();
        File selectedFile = fileChooser.showSaveDialog(stage);
        if (selectedFile != null) {
            org.ome.converter.service.analysis.GapAnalysisReportGenerator reportGenerator = new org.ome.converter.service.analysis.GapAnalysisReportGenerator();
            if (result == null) {
                result = new GapAnalysisResult(
                    datasetName,
                    OmeZarrVersion.OME_ZARR_0_5,
                    viewModel.getAllItems().size(),
                    0, 0, viewModel.getLostItems().size(),
                    viewModel.getLostItems(),
                    viewModel.getAllItems(),
                    selectedFile.toPath(),
                    null
                );
            }
            java.nio.file.Path exportedPath = reportGenerator.generateHtmlReportToFile(result, selectedFile);
            if (exportedPath != null && selectedFile.exists()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Report Exported Successfully");
                alert.setHeaderText("Metadata Gap Analysis Report Exported");
                alert.setContentText("HTML Gap Analysis Report has been exported to:\n" + selectedFile.getAbsolutePath());
                ButtonType openBtn = new ButtonType("Open Report");
                ButtonType closeBtn = new ButtonType("Close", ButtonType.CANCEL.getButtonData());
                alert.getButtonTypes().setAll(openBtn, closeBtn);
                java.util.Optional<ButtonType> res = alert.showAndWait();
                if (res.isPresent() && res.get() == openBtn) {
                    try {
                        Desktop.getDesktop().open(selectedFile);
                    } catch (Exception e) {
                        AlertHelper.showStorageError("Error Opening File", e.getMessage());
                    }
                }
            } else {
                AlertHelper.showStorageError("Export Failed", "Failed to write HTML report to selected file destination.");
            }
        }
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
            java.nio.file.Path resolved = org.ome.converter.service.runtime.BundledOmeZarrRuntimeService.getInstance().resolveDatasetPath(selectedDir.toPath());
            viewModel.complianceDatasetPathProperty().set(resolved.toAbsolutePath().toString());
            handleCheckCompliance();
        }
    }

    @FXML
    private void handleCheckCompliance() {
        try {
            String targetUrl = viewModel.startOfficialValidatorServer(null);
            if (webValidatorView != null && targetUrl != null) {
                Platform.runLater(() -> {
                    if (mainTabPane != null && tabOfficialValidator != null) {
                        mainTabPane.getSelectionModel().select(tabOfficialValidator);
                    }
                    webValidatorView.getEngine().load(targetUrl);
                });
            }
        } catch (Exception e) {
            AlertHelper.showInputValidationError("Official OME-NGFF Validator Error", e.getMessage());
        }
    }

    @FXML
    private void handleOpenOfficialValidator() {
        handleCheckCompliance();
    }

    private void openValidationSourceInNotepad() {
        try {
            if (webValidatorView == null || webValidatorView.getEngine() == null) return;
            String renderedHtml = (String) webValidatorView.getEngine().executeScript("document.documentElement.outerHTML");
            if (renderedHtml == null || renderedHtml.isBlank()) {
                AlertHelper.showInputValidationError("Source Code Error", "No rendered HTML source code available yet. Please validate a dataset first.");
                return;
            }

            File tempFile = File.createTempFile("ngff_validator_source_", ".html");
            tempFile.deleteOnExit();
            java.nio.file.Files.writeString(tempFile.toPath(), renderedHtml);

            new ProcessBuilder("notepad.exe", tempFile.getAbsolutePath()).start();
        } catch (Exception e) {
            AlertHelper.showInputValidationError("Notepad Error", "Failed to open rendered HTML source code in Notepad: " + e.getMessage());
        }
    }
}
