package org.paring;


import javafx.application.Application;
import javafx.stage.Stage;
import org.paring.config.AccessConfigs;
import org.paring.service.ExcelService;
import org.paring.service.RequestService;

public class Main extends Application {
    public static void main(String[] args) {
        AccessConfigs accessConfigs = new AccessConfigs();
        accessConfigs.init();

        ExcelService excelService = new ExcelService();
        RequestService requestService = new RequestService(accessConfigs, excelService);
        requestService.sendRequestToWordStat();
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Здесь будет ваше окно
        primaryStage.setTitle("Моё приложение");
        primaryStage.show();
    }
}