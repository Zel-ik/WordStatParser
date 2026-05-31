package org.paring.controller;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.paring.model.DateRangeInput;
import org.paring.model.PhraseBlockInput;
import org.paring.model.WorkbookDraftRowInput;
import org.paring.service.RequestService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class NewWorkbookController {
    private final RequestService requestService;
    private final Label statusLabel;
    private final Runnable afterRequestsFinished;
    private final Button submitButton = new Button("Сформировать Excel");
    private final List<DraftRowForm> draftRows = new ArrayList<>();

    NewWorkbookController(RequestService requestService, Label statusLabel, Runnable afterRequestsFinished) {
        this.requestService = requestService;
        this.statusLabel = statusLabel;
        this.afterRequestsFinished = afterRequestsFinished;
    }

    Parent createContent() {
        if (draftRows.isEmpty()) {
            addDraftRow();
        }

        BorderPane inputLayout = new BorderPane();
        inputLayout.setPadding(new Insets(10));

        VBox leftPane = new VBox(10);
        leftPane.setPrefWidth(280);
        Label leftTitle = new Label("Строки нового файла");
        leftTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Button addRowButton = new Button("Добавить строку");
        ListView<DraftRowForm> draftRowsList = new ListView<>();
        draftRowsList.getItems().addAll(draftRows);
        draftRowsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(DraftRowForm item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        addRowButton.setOnAction(event -> {
            DraftRowForm newRow = addDraftRow();
            draftRowsList.getItems().setAll(draftRows);
            draftRowsList.getSelectionModel().select(newRow);
        });
        VBox.setVgrow(draftRowsList, Priority.ALWAYS);
        leftPane.getChildren().addAll(leftTitle, addRowButton, draftRowsList);

        BorderPane centerPane = new BorderPane();
        centerPane.setPadding(new Insets(0, 0, 0, 12));
        centerPane.setCenter(new Label("Выберите строку слева для заполнения данных."));
        draftRowsList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                centerPane.setCenter(new Label("Выберите строку слева для заполнения данных."));
            } else {
                centerPane.setCenter(newValue.container());
            }
        });
        draftRowsList.getSelectionModel().selectFirst();

        Button addBlockButton = new Button("Добавить блок");
        addBlockButton.setOnAction(event -> {
            DraftRowForm selectedRow = draftRowsList.getSelectionModel().getSelectedItem();
            if (selectedRow == null) {
                UiDialogs.showError("Сначала добавьте или выберите строку слева.");
                return;
            }
            selectedRow.addPhraseBlock();
        });

        HBox actionsRow = new HBox(12, addBlockButton, submitButton);
        submitButton.setOnAction(event -> handleSubmit());

        inputLayout.setLeft(leftPane);
        inputLayout.setCenter(centerPane);
        inputLayout.setBottom(actionsRow);

        return inputLayout;
    }

    private void handleSubmit() {
        List<WorkbookDraftRowInput> rows = new ArrayList<>();
        try {
            for (DraftRowForm draftRow : draftRows) {
                rows.add(draftRow.toModel());
            }
        } catch (IllegalArgumentException exception) {
            UiDialogs.showError(exception.getMessage());
            return;
        }
        if (rows.isEmpty()) {
            UiDialogs.showError("Добавьте хотя бы одну строку для нового файла.");
            return;
        }

        Path outputPath = chooseOutputPathForNewWorkbook();
        if (outputPath == null) {
            statusLabel.setText("Создание нового Excel отменено.");
            return;
        }

        submitButton.setDisable(true);
        statusLabel.setText("Отправляем запросы и формируем Excel...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                requestService.createWorkbook(rows, outputPath);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            submitButton.setDisable(false);
            afterRequestsFinished.run();
            statusLabel.setText("Готово. Новый файл сохранен: " + outputPath.toAbsolutePath());
        });

        task.setOnFailed(event -> {
            submitButton.setDisable(false);
            afterRequestsFinished.run();
            Throwable throwable = task.getException();
            statusLabel.setText("Ошибка при выполнении запросов.");
            UiDialogs.showError(throwable == null ? "Неизвестная ошибка." : throwable.getMessage());
        });

        Thread thread = new Thread(task, "wordstat-request-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private Path chooseOutputPathForNewWorkbook() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить новый Excel файл");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel files", "*.xls"));
        fileChooser.setInitialFileName("wordstat-report.xls");

        File selectedFile = fileChooser.showSaveDialog(submitButton.getScene() == null ? null : submitButton.getScene().getWindow());
        if (selectedFile == null) {
            return null;
        }

        Path outputPath = selectedFile.toPath();
        if (!outputPath.getFileName().toString().toLowerCase().endsWith(".xls")) {
            outputPath = outputPath.resolveSibling(outputPath.getFileName() + ".xls");
        }

        if (Files.exists(outputPath)) {
            UiDialogs.showError("Файл с таким именем уже существует. Выберите другое имя или другую папку.");
            return null;
        }

        return outputPath;
    }

    private DraftRowForm addDraftRow() {
        DraftRowForm row = new DraftRowForm(draftRows.size() + 1);
        draftRows.add(row);
        return row;
    }

    private static final class PhraseBlockForm {
        private final VBox container = new VBox(10);
        private final TextField phraseField = new TextField();
        private final List<DateRangeRow> dateRangeRows = new ArrayList<>();

        private PhraseBlockForm(int blockIndex) {
            container.setPadding(new Insets(12));
            container.setStyle("-fx-border-color: #cfcfcf; -fx-border-radius: 6; -fx-background-radius: 6;");

            Label blockTitle = new Label("Блок " + blockIndex);
            blockTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

            VBox phraseBox = new VBox(6);
            Label phraseLabel = new Label("2.1 phrase сокращенное название");
            phraseField.setPromptText("Например: БГПУ");
            phraseBox.getChildren().addAll(phraseLabel, phraseField);

            VBox rangesBox = new VBox(8);
            Label rangesLabel = new Label("2.2 - 2.4 Три пары периодов для этой phrase");
            rangesBox.getChildren().add(rangesLabel);

            for (int i = 0; i < 3; i++) {
                DateRangeRow row = new DateRangeRow(i + 1);
                dateRangeRows.add(row);
                rangesBox.getChildren().add(row.container());
            }

            container.getChildren().addAll(blockTitle, phraseBox, rangesBox);
        }

        private VBox container() {
            return container;
        }

        private PhraseBlockInput toModel() {
            String phrase = phraseField.getText() == null ? "" : phraseField.getText().trim();
            if (phrase.isBlank()) {
                throw new IllegalArgumentException("Заполните сокращенное название во всех блоках.");
            }

            PhraseBlockInput input = new PhraseBlockInput();
            input.setPhrase(phrase);
            for (DateRangeRow row : dateRangeRows) {
                input.getDateRanges().add(row.toModel(phrase));
            }
            return input;
        }

        private void copyDatesFrom(PhraseBlockForm previousBlock) {
            for (int i = 0; i < dateRangeRows.size() && i < previousBlock.dateRangeRows.size(); i++) {
                dateRangeRows.get(i).copyFrom(previousBlock.dateRangeRows.get(i));
            }
        }
    }

    private static final class DateRangeRow {
        private final HBox container = new HBox(10);
        private final TextField dateFromField = new TextField();
        private final TextField dateToField = new TextField();

        private DateRangeRow(int pairIndex) {
            Label pairLabel = new Label("Период " + pairIndex);
            pairLabel.setMinWidth(90);

            dateFromField.setPromptText("from: месяц.год");
            dateToField.setPromptText("to: месяц.год");

            HBox.setHgrow(dateFromField, Priority.ALWAYS);
            HBox.setHgrow(dateToField, Priority.ALWAYS);

            Button fromPickerButton = UiDialogs.createMonthPickerButton(dateFromField, () -> {});
            Button toPickerButton = UiDialogs.createMonthPickerButton(dateToField, () -> {});

            container.getChildren().addAll(pairLabel, dateFromField, fromPickerButton, dateToField, toPickerButton);
        }

        private HBox container() {
            return container;
        }

        private DateRangeInput toModel(String phrase) {
            String dateFrom = dateFromField.getText() == null ? "" : dateFromField.getText().trim();
            String dateTo = dateToField.getText() == null ? "" : dateToField.getText().trim();

            if (!dateFrom.matches("\\d{2}\\.\\d{4}") || !dateTo.matches("\\d{2}\\.\\d{4}")) {
                throw new IllegalArgumentException("Для phrase \"" + phrase + "\" даты должны быть в формате месяц.год.");
            }

            return new DateRangeInput(dateFrom, dateTo);
        }

        private void copyFrom(DateRangeRow source) {
            dateFromField.setText(source.dateFromField.getText());
            dateToField.setText(source.dateToField.getText());
        }
    }

    private static final class DraftRowForm {
        private final VBox content = new VBox(16);
        private final TextField universityNameField = new TextField();
        private final VBox blocksContainer = new VBox(16);
        private final List<PhraseBlockForm> blockForms = new ArrayList<>();
        private final int rowNumber;
        private final ScrollPane container = new ScrollPane();

        private DraftRowForm(int rowNumber) {
            this.rowNumber = rowNumber;
            content.setPadding(new Insets(10));

            Label universityLabel = new Label("Полное название учебного заведения");
            universityLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
            universityNameField.setPromptText("Например: Федеральное государственное бюджетное образовательное учреждение...");
            VBox universityBox = new VBox(8, universityLabel, universityNameField);
            universityBox.setPadding(new Insets(12));
            universityBox.setStyle("-fx-border-color: #cfcfcf; -fx-border-radius: 6; -fx-background-radius: 6;");

            Label blocksLabel = new Label("Блоки запросов");
            content.getChildren().addAll(universityBox, blocksLabel, blocksContainer);

            container.setContent(content);
            container.setFitToWidth(true);

            addPhraseBlock();
        }

        private Parent container() {
            return container;
        }

        private void addPhraseBlock() {
            PhraseBlockForm form = new PhraseBlockForm(blockForms.size() + 1);
            if (!blockForms.isEmpty()) {
                form.copyDatesFrom(blockForms.get(blockForms.size() - 1));
            }
            blockForms.add(form);
            blocksContainer.getChildren().add(form.container());
        }

        private String getDisplayName() {
            String universityName = universityNameField.getText() == null ? "" : universityNameField.getText().trim();
            return universityName.isBlank() ? "Новая строка " + rowNumber : universityName;
        }

        private WorkbookDraftRowInput toModel() {
            String universityName = universityNameField.getText() == null ? "" : universityNameField.getText().trim();
            if (universityName.isBlank()) {
                throw new IllegalArgumentException("Заполните полное название учебного заведения во всех строках.");
            }

            WorkbookDraftRowInput row = new WorkbookDraftRowInput();
            row.setUniversityName(universityName);
            for (PhraseBlockForm blockForm : blockForms) {
                row.getBlocks().add(blockForm.toModel());
            }
            return row;
        }
    }
}
