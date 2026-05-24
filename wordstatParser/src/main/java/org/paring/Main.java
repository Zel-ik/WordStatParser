package org.paring;


import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.paring.config.AccessConfigs;
import org.paring.controller.GUIController;
import org.paring.service.ExcelService;
import org.paring.service.RequestService;

public class Main extends Application {
    static {
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.forceGPU", "false");
        System.setProperty("quantum.multithreaded", "false");
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        AccessConfigs accessConfigs = new AccessConfigs();
        accessConfigs.init();

        ExcelService excelService = new ExcelService();
        RequestService requestService = new RequestService(accessConfigs, excelService);
        GUIController guiController = new GUIController(requestService);

        Scene scene = new Scene(guiController.createContent(), 1100, 800);
        primaryStage.setTitle("Wordstat Parser");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(650);
        primaryStage.show();
    }
}
