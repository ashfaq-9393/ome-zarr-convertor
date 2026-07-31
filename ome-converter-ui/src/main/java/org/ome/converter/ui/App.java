package org.ome.converter.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class App extends Application {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    @Override
    public void start(Stage primaryStage) throws Exception {
        log.info("Starting VSI Studio Pro Desktop Converter UI...");

        URL fxmlUrl = getClass().getResource("/fxml/main_dashboard.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("Cannot find FXML layout: /fxml/main_dashboard.fxml");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        Scene scene = new Scene(root, 960, 640);

        primaryStage.setTitle("VSI Studio Pro — Microscopic Slide Converter");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(850);
        primaryStage.setMinHeight(550);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
