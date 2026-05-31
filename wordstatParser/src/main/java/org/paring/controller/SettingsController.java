package org.paring.controller;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.paring.service.RequestService;

final class SettingsController {
    private static final int DEFAULT_DAILY_REQUEST_LIMIT = 1000;

    private final RequestService requestService;
    private final Label statusLabel;
    private final Runnable afterSettingsApplied;

    SettingsController(RequestService requestService, Label statusLabel, Runnable afterSettingsApplied) {
        this.requestService = requestService;
        this.statusLabel = statusLabel;
        this.afterSettingsApplied = afterSettingsApplied;
    }

    Parent createContent() {
        PasswordField tokenField = new PasswordField();
        tokenField.setPromptText("Новый токен Wordstat API");
        TextField requestLimitField = new TextField(String.valueOf(requestService.getDailyRequestLimit()));
        requestLimitField.setPromptText(String.valueOf(DEFAULT_DAILY_REQUEST_LIMIT));
        Label counterStateLabel = new Label(buildRequestCounterText());
        Button saveButton = new Button("Сохранить настройки");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(16));
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Токен Yandex"), 0, 0);
        grid.add(tokenField, 1, 0);
        grid.add(new Label("Лимит запросов в день"), 0, 1);
        grid.add(requestLimitField, 1, 1);
        grid.add(new Label("Счетчик"), 0, 2);
        grid.add(counterStateLabel, 1, 2);
        grid.add(saveButton, 1, 3);
        tokenField.setPrefColumnCount(42);

        saveButton.setOnAction(event -> {
            applySettings(tokenField.getText(), requestLimitField.getText());
            tokenField.clear();
            counterStateLabel.setText(buildRequestCounterText());
        });

        return grid;
    }

    private void applySettings(String authToken, String dailyRequestLimit) {
        try {
            String normalizedToken = authToken == null ? "" : authToken.trim();
            if (!normalizedToken.isBlank()) {
                requestService.updateAuthToken(normalizedToken);
            }

            String normalizedLimit = dailyRequestLimit == null ? "" : dailyRequestLimit.trim();
            if (!normalizedLimit.isBlank()) {
                requestService.updateDailyRequestLimit(Integer.parseInt(normalizedLimit));
            }

            afterSettingsApplied.run();
            statusLabel.setText("Настройки Wordstat обновлены для текущего запуска приложения.");
        } catch (NumberFormatException exception) {
            UiDialogs.showError("Лимит запросов должен быть целым числом.");
        } catch (IllegalArgumentException exception) {
            UiDialogs.showError(exception.getMessage());
        }
    }

    private String buildRequestCounterText() {
        return "Запросы Wordstat сегодня: " + requestService.getRequestsSentToday()
                + " из " + requestService.getDailyRequestLimit()
                + ", осталось " + requestService.getRemainingRequestsToday() + ".";
    }
}
