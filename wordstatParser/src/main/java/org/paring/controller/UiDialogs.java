package org.paring.controller;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.time.YearMonth;
import java.util.Optional;

final class UiDialogs {
    private static final String[] MONTH_NAMES = {
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    };

    private UiDialogs() {
    }

    static void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    static Button createMonthPickerButton(TextField targetField, Runnable afterSelection) {
        Button button = new Button("Выбрать");
        button.setOnAction(event -> showMonthPicker(targetField, afterSelection));
        return button;
    }

    private static void showMonthPicker(TextField targetField, Runnable afterSelection) {
        YearMonth selectedMonth = parseYearMonth(targetField.getText());
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Выбор месяца");
        dialog.setHeaderText(null);

        ButtonType applyButton = new ButtonType("Выбрать", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButton, ButtonType.CANCEL);

        ComboBox<MonthOption> monthBox = new ComboBox<>();
        for (int month = 1; month <= MONTH_NAMES.length; month++) {
            monthBox.getItems().add(new MonthOption(month, MONTH_NAMES[month - 1]));
        }
        monthBox.getSelectionModel().select(selectedMonth.getMonthValue() - 1);

        ComboBox<Integer> yearBox = new ComboBox<>();
        int currentYear = YearMonth.now().getYear();
        for (int year = currentYear - 15; year <= currentYear + 5; year++) {
            yearBox.getItems().add(year);
        }
        yearBox.getSelectionModel().select(Integer.valueOf(selectedMonth.getYear()));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Месяц"), 0, 0);
        grid.add(monthBox, 1, 0);
        grid.add(new Label("Год"), 0, 1);
        grid.add(yearBox, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != applyButton) {
                return null;
            }
            MonthOption month = monthBox.getSelectionModel().getSelectedItem();
            Integer year = yearBox.getSelectionModel().getSelectedItem();
            if (month == null || year == null) {
                return null;
            }
            return String.format("%02d.%04d", month.value(), year);
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(value -> {
            targetField.setText(value);
            afterSelection.run();
        });
    }

    private static YearMonth parseYearMonth(String value) {
        if (value != null && value.trim().matches("\\d{2}\\.\\d{4}")) {
            String[] parts = value.trim().split("\\.");
            return YearMonth.of(Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
        }
        return YearMonth.now();
    }

    private record MonthOption(int value, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
