package org.ome.converter.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

public class AlertHelper {

    public static void showInputValidationError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Invalid Input Source File");
        alert.setHeaderText(header);
        alert.setContentText(content + "\n\nPlease select a valid Olympus CellSens (.vsi) microscopic image file.");
        alert.showAndWait();
    }

    public static void showStorageError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Target Destination Storage Warning");
        alert.setHeaderText(header);
        alert.setContentText(content + "\n\nPlease choose a local output directory with sufficient free space and write permissions.");
        alert.showAndWait();
    }

    public static void showConversionError(String header, String message, Throwable cause) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Conversion Failure Error");
        alert.setHeaderText(header);
        alert.setContentText(message);

        if (cause != null) {
            StringWriter sw = new StringWriter();
            cause.printStackTrace(new PrintWriter(sw));
            TextArea textArea = new TextArea(sw.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);

            VBox content = new VBox(textArea);
            alert.getDialogPane().setExpandableContent(content);
        }

        alert.showAndWait();
    }

    public static void showCompletionSuccess(String jobId, String targetPathStr) {
        showCompletionSuccessWithReport(jobId, targetPathStr, null);
    }

    public static void showCompletionSuccessWithReport(String jobId, String targetPathStr, File htmlReportFile) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Conversion & Metadata Gap Analysis Complete");
        alert.setHeaderText("OME-Zarr Dataset & Gap Analysis Report Generated!");
        
        String text = "The conversion completed successfully.\nOutput Dataset:\n" + targetPathStr;
        if (htmlReportFile != null && htmlReportFile.exists()) {
            text += "\n\nMetadata Gap Analysis HTML Dashboard:\n" + htmlReportFile.getAbsolutePath();
        }
        alert.setContentText(text);

        ButtonType openReportBtn = new ButtonType("Open Gap Report (HTML)");
        ButtonType openFolderBtn = new ButtonType("Open Output Folder");
        ButtonType closeBtn = new ButtonType("Close", ButtonType.CANCEL.getButtonData());

        if (htmlReportFile != null && htmlReportFile.exists()) {
            alert.getButtonTypes().setAll(openReportBtn, openFolderBtn, closeBtn);
        } else {
            alert.getButtonTypes().setAll(openFolderBtn, closeBtn);
        }

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == openReportBtn && htmlReportFile != null && htmlReportFile.exists()) {
                try {
                    Desktop.getDesktop().browse(htmlReportFile.toURI());
                } catch (Exception e) {
                    showStorageError("Failed to open HTML report", e.getMessage());
                }
            } else if (result.get() == openFolderBtn) {
                try {
                    File dir = new File(targetPathStr);
                    if (dir.exists()) {
                        Desktop.getDesktop().open(dir.isDirectory() ? dir : dir.getParentFile());
                    }
                } catch (Exception e) {
                    showStorageError("Failed to open directory", e.getMessage());
                }
            }
        }
    }
}
