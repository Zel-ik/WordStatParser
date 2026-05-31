package org.paring.controller;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.paring.model.DateRangeInput;
import org.paring.model.ExcelPreviewBlock;
import org.paring.model.ExcelPreviewResult;
import org.paring.model.ExcelPreviewSheet;
import org.paring.model.ExcelPreviewUniversity;
import org.paring.service.RequestService;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GUIController {
    private final RequestService requestService;
    private final Label statusLabel = new Label();
    private final Label workbookTitleLabel = new Label("Файл не выбран");
    private final Label requestCounterLabel = new Label();
    private final StackPane mainContentPane = new StackPane();
    private final ExistingWorkbookController existingWorkbookController;
    private Parent existingWorkbookContent;
    private Parent inputContent;
    private Parent settingsContent;
    private Path selectedExcelPath;

    public GUIController(RequestService requestService) {
        this.requestService = requestService;
        this.existingWorkbookController = new ExistingWorkbookController(
                this::createPreviewContent,
                this::addSheetToSelectedWorkbook,
                this::showSheetPeriodsDialog
        );
    }

    public Parent createContent() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        VBox topContent = new VBox(16);
        topContent.setPadding(new Insets(0, 8, 12, 0));

        workbookTitleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Button chooseExcelButton = new Button("Выбрать Excel");
        chooseExcelButton.setOnAction(event -> chooseExcelFile());
        Button createExcelButton = new Button("Создать новый Excel");
        createExcelButton.setOnAction(event -> showInputContent());
        Button settingsButton = new Button("Настройки");
        settingsButton.setOnAction(event -> showSettingsContent());
        HBox navigationBox = new HBox(12, chooseExcelButton, createExcelButton, settingsButton);

        statusLabel.setWrapText(true);
        updateRequestCounterLabel();

        topContent.getChildren().addAll(navigationBox, workbookTitleLabel, requestCounterLabel, statusLabel);

        existingWorkbookContent = existingWorkbookController.createContent();
        inputContent = new NewWorkbookController(requestService, statusLabel, this::updateRequestCounterLabel).createContent();
        settingsContent = new SettingsController(requestService, statusLabel, this::updateRequestCounterLabel).createContent();
        showExistingWorkbookContent();

        root.setTop(topContent);
        root.setCenter(mainContentPane);
        return root;
    }

    private void showExistingWorkbookContent() {
        mainContentPane.getChildren().setAll(existingWorkbookContent);
    }

    private void showInputContent() {
        mainContentPane.getChildren().setAll(inputContent);
    }

    private void showSettingsContent() {
        mainContentPane.getChildren().setAll(settingsContent);
    }

    private void updateRequestCounterLabel() {
        requestCounterLabel.setText(buildRequestCounterText());
    }

    private String buildRequestCounterText() {
        return "Запросы Wordstat сегодня: " + requestService.getRequestsSentToday()
                + " из " + requestService.getDailyRequestLimit()
                + ", осталось " + requestService.getRemainingRequestsToday() + ".";
    }

    private Parent createPreviewContent(ExcelPreviewSheet previewSheet) {
        if (previewSheet.getUniversities().isEmpty()) {
            VBox emptyState = new VBox(16);
            emptyState.setPadding(new Insets(14));
            emptyState.getChildren().add(new Label("На этом листе не найдено данных для отображения."));
            return emptyState;
        }

        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.28);

        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(14));
        leftPane.setPrefWidth(280);
        Label leftTitle = new Label("Строки листа");
        leftTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        ListView<ExcelPreviewUniversity> universityList = new ListView<>();
        universityList.getItems().addAll(previewSheet.getUniversities());
        universityList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        universityList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ExcelPreviewUniversity item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getUniversityName());
                }
            }
        });
        Button refillSelectedRowsButton = new Button("Переотправить выбранные строки");
        refillSelectedRowsButton.setOnAction(event -> refillSelectedUniversities(universityList, refillSelectedRowsButton));
        VBox.setVgrow(universityList, Priority.ALWAYS);
        leftPane.getChildren().addAll(leftTitle, refillSelectedRowsButton, universityList);

        BorderPane detailsPane = new BorderPane();
        detailsPane.setPadding(new Insets(14));
        detailsPane.setCenter(buildEmptyDetailsState());

        universityList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                detailsPane.setCenter(buildEmptyDetailsState());
            } else {
                detailsPane.setCenter(createUniversityDetails(newValue));
            }
        });

        ExcelPreviewUniversity universityToSelect = existingWorkbookController.findUniversityToSelectAfterReload(previewSheet);
        if (universityToSelect != null) {
            universityList.getSelectionModel().select(universityToSelect);
            existingWorkbookController.clearRowSelectionRestoreRequest();
        } else if (!previewSheet.getUniversities().isEmpty()) {
            universityList.getSelectionModel().selectFirst();
        }

        splitPane.getItems().addAll(leftPane, detailsPane);
        return splitPane;
    }

    private Parent createUniversityDetails(ExcelPreviewUniversity university) {
        VBox universityCard = new VBox(10);
        universityCard.setPadding(new Insets(12));
        universityCard.setStyle("-fx-border-color: #d8d8d8; -fx-border-radius: 6; -fx-background-radius: 6;");

        VBox fullNameBlock = new VBox(8);
        fullNameBlock.setPadding(new Insets(12));
        fullNameBlock.setStyle("-fx-border-color: #cfcfcf; -fx-border-radius: 6; -fx-background-radius: 6;");
        Label fullNameLabel = new Label("Полное название учебного заведения");
        fullNameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        TextField fullNameField = new TextField(university.getUniversityName());
        fullNameField.setPromptText("Полное название учебного заведения");
        fullNameBlock.getChildren().addAll(fullNameLabel, fullNameField);
        universityCard.getChildren().add(fullNameBlock);

        List<BlockEditorState> blockEditorStates = new ArrayList<>();
        if (!university.getFullNameResults().isEmpty()) {
            Button refillFullNameButton = new Button("Переотправить полное название в Excel");
            List<EditableResultRow> fullNameRows = new ArrayList<>();
            ExcelPreviewBlock fullNameBlockEditor = buildFullNameBlock(university);
            BlockEditorState fullNameEditorState = new BlockEditorState(
                    university,
                    fullNameField,
                    fullNameBlockEditor,
                    fullNameField,
                    fullNameRows,
                    refillFullNameButton
            );
            blockEditorStates.add(fullNameEditorState);
            refillFullNameButton.setOnAction(event -> fullNameEditorState.refresh(true));
            fullNameBlock.getChildren().add(refillFullNameButton);

            for (int i = 0; i < university.getFullNameResults().size(); i++) {
                ExcelPreviewResult result = university.getFullNameResults().get(i);
                EditableResultRow editableRow = new EditableResultRow(result, i == 0);
                fullNameRows.add(editableRow);
                fullNameBlock.getChildren().add(editableRow.container());
            }
        }

        for (ExcelPreviewBlock block : university.getBlocks()) {
            VBox blockBox = new VBox(6);
            blockBox.setPadding(new Insets(8, 0, 8, 16));

            Label phraseLabel = new Label("Сокращение");
            TextField phraseField = new TextField(block.getPhrase());
            Button refillButton = new Button("Переотправить блок в Excel");
            List<EditableResultRow> editableRows = new ArrayList<>();

            BlockEditorState blockEditorState = new BlockEditorState(university, fullNameField, block, phraseField, editableRows, refillButton);
            blockEditorStates.add(blockEditorState);
            refillButton.setOnAction(event -> blockEditorState.refresh(true));
            blockBox.getChildren().addAll(phraseLabel, phraseField, refillButton);

            for (int i = 0; i < block.getResults().size(); i++) {
                ExcelPreviewResult result = block.getResults().get(i);
                EditableResultRow editableRow = new EditableResultRow(result, i == 0);
                editableRows.add(editableRow);
                blockBox.getChildren().add(editableRow.container());
            }

            universityCard.getChildren().add(blockBox);
        }

        ScrollPane scrollPane = new ScrollPane(universityCard);
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }

    private ExcelPreviewBlock buildFullNameBlock(ExcelPreviewUniversity university) {
        ExcelPreviewBlock block = new ExcelPreviewBlock();
        block.setSheetName(university.getSheetName());
        block.setHeaderRowIndex(university.getHeaderRowIndex());
        block.setRowIndex(university.getRowIndex());
        block.setPhraseColumnIndex(university.getFullNameColumnIndex());
        block.setPhrase(university.getUniversityName());
        block.getResults().addAll(university.getFullNameResults());
        return block;
    }

    private void showSheetPeriodsDialog() {
        if (selectedExcelPath == null) {
            UiDialogs.showError("Сначала выберите Excel файл.");
            return;
        }

        ExcelPreviewSheet selectedSheet = existingWorkbookController.getSelectedPreviewSheet();
        if (selectedSheet == null) {
            UiDialogs.showError("Выберите лист Excel для задания периодов.");
            return;
        }

        Dialog<List<DateRangeInput>> dialog = new Dialog<>();
        dialog.setTitle("Задать периоды дат");
        dialog.setHeaderText(null);

        ButtonType applyButton = new ButtonType("Сохранить периоды", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButton, ButtonType.CANCEL);

        VBox content = new VBox(12);
        content.setPadding(new Insets(10));
        Label memo = new Label("Периоды добавляются только для выбранного листа.");
        memo.setStyle("-fx-font-weight: bold;");
        content.getChildren().add(memo);

        List<SheetPeriodInputRow> periodRows = new ArrayList<>();
        List<ExcelPreviewResult> currentPeriods = findCurrentSheetPeriods(selectedSheet);
        for (int i = 0; i < 3; i++) {
            ExcelPreviewResult currentPeriod = i < currentPeriods.size() ? currentPeriods.get(i) : null;
            SheetPeriodInputRow row = new SheetPeriodInputRow(i + 1, currentPeriod);
            periodRows.add(row);
            content.getChildren().add(row.container());
        }

        dialog.getDialogPane().setContent(content);
        Button applyDialogButton = (Button) dialog.getDialogPane().lookupButton(applyButton);
        applyDialogButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                for (SheetPeriodInputRow row : periodRows) {
                    row.toModel();
                }
            } catch (IllegalArgumentException exception) {
                event.consume();
                UiDialogs.showError(exception.getMessage());
            }
        });
        dialog.setResultConverter(button -> {
            if (button != applyButton) {
                return null;
            }

            List<DateRangeInput> dateRanges = new ArrayList<>();
            for (SheetPeriodInputRow row : periodRows) {
                dateRanges.add(row.toModel());
            }
            return dateRanges;
        });

        Optional<List<DateRangeInput>> result = dialog.showAndWait();
        result.ifPresent(dateRanges -> updateSelectedSheetPeriods(selectedSheet, currentPeriods, dateRanges));
    }

    private List<ExcelPreviewResult> findCurrentSheetPeriods(ExcelPreviewSheet previewSheet) {
        for (ExcelPreviewUniversity university : previewSheet.getUniversities()) {
            if (!university.getFullNameResults().isEmpty()) {
                return university.getFullNameResults();
            }
            for (ExcelPreviewBlock block : university.getBlocks()) {
                if (!block.getResults().isEmpty()) {
                    return block.getResults();
                }
            }
        }
        return List.of();
    }

    private void updateSelectedSheetPeriods(ExcelPreviewSheet selectedSheet,
                                            List<ExcelPreviewResult> currentPeriods,
                                            List<DateRangeInput> dateRanges) {
        statusLabel.setText("Обновляем периоды выбранного листа...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                requestService.updateSheetPeriods(selectedExcelPath, selectedSheet.getSheetName(), dateRanges);

                for (int periodIndex = 0; periodIndex < dateRanges.size(); periodIndex++) {
                    if (!isPeriodChanged(currentPeriods, periodIndex, dateRanges.get(periodIndex))) {
                        continue;
                    }
                    refillChangedPeriod(selectedSheet, periodIndex, dateRanges.get(periodIndex));
                }
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            updateRequestCounterLabel();
            statusLabel.setText("Периоды выбранного листа обновлены.");
            reloadWorkbookPreview();
        });

        task.setOnFailed(event -> {
            updateRequestCounterLabel();
            statusLabel.setText("Не удалось обновить периоды выбранного листа.");
            Throwable throwable = task.getException();
            UiDialogs.showError(throwable == null ? "Неизвестная ошибка." : throwable.getMessage());
        });

        Thread thread = new Thread(task, "wordstat-update-sheet-periods-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean isPeriodChanged(List<ExcelPreviewResult> currentPeriods, int periodIndex, DateRangeInput dateRange) {
        if (periodIndex >= currentPeriods.size()) {
            return true;
        }

        ExcelPreviewResult currentPeriod = currentPeriods.get(periodIndex);
        return !dateRange.getDateFrom().equals(currentPeriod.getMonthFrom())
                || !dateRange.getDateTo().equals(currentPeriod.getMonthTo());
    }

    private void refillChangedPeriod(ExcelPreviewSheet selectedSheet, int periodIndex, DateRangeInput dateRange) {
        for (ExcelPreviewUniversity university : selectedSheet.getUniversities()) {
            if (periodIndex < university.getFullNameResults().size()) {
                requestService.refillBlock(
                        selectedExcelPath,
                        university,
                        buildSingleResultFullNameBlock(university, periodIndex, dateRange)
                );
            }

            for (ExcelPreviewBlock block : university.getBlocks()) {
                if (periodIndex < block.getResults().size()) {
                    requestService.refillBlock(
                            selectedExcelPath,
                            university,
                            buildSingleResultBlock(block, periodIndex, dateRange)
                    );
                }
            }
        }
    }

    private ExcelPreviewBlock buildSingleResultFullNameBlock(ExcelPreviewUniversity university,
                                                             int periodIndex,
                                                             DateRangeInput dateRange) {
        ExcelPreviewBlock block = buildFullNameBlock(university);
        block.getResults().clear();
        block.getResults().add(buildEditedResult(university.getFullNameResults().get(periodIndex), dateRange));
        return block;
    }

    private ExcelPreviewBlock buildSingleResultBlock(ExcelPreviewBlock sourceBlock,
                                                     int periodIndex,
                                                     DateRangeInput dateRange) {
        ExcelPreviewBlock block = new ExcelPreviewBlock();
        block.setSheetName(sourceBlock.getSheetName());
        block.setHeaderRowIndex(sourceBlock.getHeaderRowIndex());
        block.setRowIndex(sourceBlock.getRowIndex());
        block.setPhraseColumnIndex(sourceBlock.getPhraseColumnIndex());
        block.setPhrase(sourceBlock.getPhrase());
        block.getResults().add(buildEditedResult(sourceBlock.getResults().get(periodIndex), dateRange));
        return block;
    }

    private ExcelPreviewResult buildEditedResult(ExcelPreviewResult sourceResult, DateRangeInput dateRange) {
        ExcelPreviewResult result = new ExcelPreviewResult();
        result.setColumnIndex(sourceResult.getColumnIndex());
        result.setMonthFrom(dateRange.getDateFrom());
        result.setMonthTo(dateRange.getDateTo());
        result.setDate("01." + dateRange.getDateFrom() + " - 01." + dateRange.getDateTo());
        return result;
    }

    private void refillSelectedUniversities(ListView<ExcelPreviewUniversity> universityList, Button refillButton) {
        if (selectedExcelPath == null) {
            UiDialogs.showError("Сначала выберите Excel файл.");
            return;
        }

        List<ExcelPreviewUniversity> selectedUniversities = new ArrayList<>(
                universityList.getSelectionModel().getSelectedItems()
        );
        if (selectedUniversities.isEmpty()) {
            UiDialogs.showError("Выберите одну или несколько строк слева.");
            return;
        }
        existingWorkbookController.rememberRowSelectionAfterReload(universityList.getSelectionModel().getSelectedItem());

        refillButton.setDisable(true);
        statusLabel.setText("Переотправляем выбранные строки и сохраняем значения в Excel...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (ExcelPreviewUniversity university : selectedUniversities) {
                    if (!university.getFullNameResults().isEmpty()) {
                        requestService.refillBlock(selectedExcelPath, university, buildFullNameBlock(university));
                    }
                    for (ExcelPreviewBlock block : university.getBlocks()) {
                        requestService.refillBlock(selectedExcelPath, university, block);
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            refillButton.setDisable(false);
            updateRequestCounterLabel();
            statusLabel.setText("Выбранные строки обновлены в Excel.");
            reloadWorkbookPreview();
        });

        task.setOnFailed(event -> {
            refillButton.setDisable(false);
            updateRequestCounterLabel();
            statusLabel.setText("Не удалось переотправить выбранные строки.");
            Throwable throwable = task.getException();
            UiDialogs.showError(throwable == null ? "Неизвестная ошибка." : throwable.getMessage());
        });

        Thread thread = new Thread(task, "wordstat-refill-selected-rows-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private Parent buildEmptyDetailsState() {
        VBox emptyState = new VBox(8);
        emptyState.setPadding(new Insets(12));
        emptyState.getChildren().add(new Label("Выберите строку слева, чтобы посмотреть и редактировать ее блоки."));
        return emptyState;
    }

    private void chooseExcelFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите Excel файл");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel files", "*.xls", "*.xlsx"));

        File selectedFile = fileChooser.showOpenDialog(mainContentPane.getScene() == null ? null : mainContentPane.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        selectedExcelPath = selectedFile.toPath();
        showExistingWorkbookContent();
        workbookTitleLabel.setText(selectedFile.getName());
        existingWorkbookController.setSelectedExcelPath(selectedFile.getAbsolutePath());
        statusLabel.setText("Читаем страницы выбранного Excel...");
        reloadWorkbookPreview();
    }

    private void addSheetToSelectedWorkbook() {
        if (selectedExcelPath == null) {
            UiDialogs.showError("Сначала выберите Excel файл.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("Новый лист");
        dialog.setTitle("Добавить лист");
        dialog.setHeaderText(null);
        dialog.setContentText("Название листа:");

        dialog.showAndWait().ifPresent(sheetName -> {
            try {
                String createdSheetName = requestService.addSheetToWorkbook(selectedExcelPath, sheetName);
                statusLabel.setText("Лист \"" + createdSheetName + "\" добавлен в выбранный Excel.");
                reloadWorkbookPreview();
            } catch (RuntimeException exception) {
                UiDialogs.showError(exception.getMessage());
            }
        });
    }

    private boolean refillSingleBlock(ExcelPreviewUniversity university,
                                      TextField universityNameField,
                                      ExcelPreviewBlock block,
                                      TextField phraseField,
                                      List<EditableResultRow> editableRows,
                                      Button refillButton,
                                      Runnable afterSuccess,
                                      Runnable afterCompletion) {
        if (selectedExcelPath == null) {
            statusLabel.setText("Сначала выберите Excel файл.");
            return false;
        }

        String universityName = universityNameField.getText() == null ? "" : universityNameField.getText().trim();
        if (universityName.isBlank()) {
            statusLabel.setText("Автообновление ожидает заполнения полного названия учебного заведения.");
            return false;
        }

        ExcelPreviewBlock editedBlock;
        try {
            editedBlock = buildEditedBlock(block, phraseField, editableRows);
        } catch (IllegalArgumentException exception) {
            statusLabel.setText("Автообновление ожидает корректного формата дат.");
            return false;
        }

        refillButton.setDisable(true);
        statusLabel.setText("Переотправляем выбранный блок и обновляем Excel...");

        Task<List<ExcelPreviewResult>> task = new Task<>() {
            @Override
            protected List<ExcelPreviewResult> call() {
                ExcelPreviewUniversity editedUniversity = new ExcelPreviewUniversity();
                editedUniversity.setSheetName(university.getSheetName());
                editedUniversity.setHeaderRowIndex(university.getHeaderRowIndex());
                editedUniversity.setRowIndex(university.getRowIndex());
                editedUniversity.setFullNameColumnIndex(university.getFullNameColumnIndex());
                editedUniversity.setUniversityName(universityName);
                return requestService.refillBlock(selectedExcelPath, editedUniversity, editedBlock);
            }
        };

        task.setOnSucceeded(event -> {
            refillButton.setDisable(false);
            updateRequestCounterLabel();
            university.setUniversityName(universityName);
            universityNameField.setText(universityName);
            phraseField.setText(editedBlock.getPhrase());
            updateEditableRows(editableRows, task.getValue());
            statusLabel.setText("Блок обновлен в выбранном Excel.");
            afterSuccess.run();
            afterCompletion.run();
        });

        task.setOnFailed(event -> {
            refillButton.setDisable(false);
            updateRequestCounterLabel();
            statusLabel.setText("Не удалось обновить блок.");
            Throwable throwable = task.getException();
            UiDialogs.showError(throwable == null ? "Неизвестная ошибка." : throwable.getMessage());
            afterCompletion.run();
        });

        Thread thread = new Thread(task, "wordstat-refill-block-thread");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private ExcelPreviewBlock buildEditedBlock(ExcelPreviewBlock block,
                                               TextField phraseField,
                                               List<EditableResultRow> editableRows) {
        String phrase = phraseField.getText() == null ? "" : phraseField.getText().trim();
        if (phrase.isBlank()) {
            throw new IllegalArgumentException("Сокращение не должно быть пустым.");
        }

        ExcelPreviewBlock editedBlock = new ExcelPreviewBlock();
        editedBlock.setSheetName(block.getSheetName());
        editedBlock.setHeaderRowIndex(block.getHeaderRowIndex());
        editedBlock.setRowIndex(block.getRowIndex());
        editedBlock.setPhraseColumnIndex(block.getPhraseColumnIndex());
        editedBlock.setPhrase(phrase);

        for (EditableResultRow editableRow : editableRows) {
            editedBlock.getResults().add(editableRow.toResult(phrase));
        }
        return editedBlock;
    }

    private void updateEditableRows(List<EditableResultRow> editableRows, List<ExcelPreviewResult> updatedResults) {
        for (int i = 0; i < editableRows.size() && i < updatedResults.size(); i++) {
            editableRows.get(i).applyUpdatedResult(updatedResults.get(i));
        }
    }

    private final class BlockEditorState {
        private final ExcelPreviewUniversity university;
        private final TextField universityNameField;
        private final ExcelPreviewBlock block;
        private final TextField phraseField;
        private final List<EditableResultRow> editableRows;
        private final Button refillButton;
        private boolean refreshInFlight;
        private String lastSubmittedSnapshot;

        private BlockEditorState(ExcelPreviewUniversity university,
                                 TextField universityNameField,
                                 ExcelPreviewBlock block,
                                 TextField phraseField,
                                 List<EditableResultRow> editableRows,
                                 Button refillButton) {
            this.university = university;
            this.universityNameField = universityNameField;
            this.block = block;
            this.phraseField = phraseField;
            this.editableRows = editableRows;
            this.refillButton = refillButton;
            this.lastSubmittedSnapshot = captureSnapshot();
        }

        private void refresh(boolean force) {
            if (refreshInFlight) {
                return;
            }

            String currentSnapshot = captureSnapshot();
            if (currentSnapshot.equals(lastSubmittedSnapshot)) {
                return;
            }

            boolean started = refillSingleBlock(
                    university,
                    universityNameField,
                    block,
                    phraseField,
                    editableRows,
                    refillButton,
                    () -> {
                        lastSubmittedSnapshot = captureSnapshot();
                    },
                    () -> {
                        refreshInFlight = false;
                    }
            );
            refreshInFlight = started;
        }

        private String captureSnapshot() {
            StringBuilder snapshot = new StringBuilder();
            snapshot.append(universityNameField.getText() == null ? "" : universityNameField.getText().trim());
            snapshot.append('|');
            snapshot.append(phraseField.getText() == null ? "" : phraseField.getText().trim());
            for (EditableResultRow editableRow : editableRows) {
                snapshot.append('|').append(editableRow.snapshot());
            }
            return snapshot.toString();
        }
    }

    private void reloadWorkbookPreview() {
        if (selectedExcelPath == null) {
            return;
        }

        Task<List<ExcelPreviewSheet>> task = new Task<>() {
            @Override
            protected List<ExcelPreviewSheet> call() {
                return requestService.readWorkbookPreview(selectedExcelPath);
            }
        };

        task.setOnSucceeded(event -> {
            existingWorkbookController.rebuildTabs(task.getValue());
            statusLabel.setText("");
        });

        task.setOnFailed(event -> {
            statusLabel.setText("Не удалось загрузить Excel.");
            Throwable throwable = task.getException();
            UiDialogs.showError(throwable == null ? "Неизвестная ошибка при чтении Excel." : throwable.getMessage());
        });

        Thread thread = new Thread(task, "excel-preview-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private static final class SheetPeriodInputRow {
        private final HBox container = new HBox(10);
        private final TextField dateFromField = new TextField();
        private final TextField dateToField = new TextField();

        private SheetPeriodInputRow(int periodIndex, ExcelPreviewResult currentPeriod) {
            Label label = new Label("Период " + periodIndex);
            label.setMinWidth(90);
            dateFromField.setPromptText("from: месяц.год");
            dateToField.setPromptText("to: месяц.год");
            if (currentPeriod != null) {
                dateFromField.setText(currentPeriod.getMonthFrom());
                dateToField.setText(currentPeriod.getMonthTo());
            }

            HBox.setHgrow(dateFromField, Priority.ALWAYS);
            HBox.setHgrow(dateToField, Priority.ALWAYS);

            Button fromPickerButton = UiDialogs.createMonthPickerButton(dateFromField, () -> {});
            Button toPickerButton = UiDialogs.createMonthPickerButton(dateToField, () -> {});

            container.getChildren().addAll(label, dateFromField, fromPickerButton, dateToField, toPickerButton);
        }

        private HBox container() {
            return container;
        }

        private DateRangeInput toModel() {
            String dateFrom = dateFromField.getText() == null ? "" : dateFromField.getText().trim();
            String dateTo = dateToField.getText() == null ? "" : dateToField.getText().trim();

            if (!dateFrom.matches("\\d{2}\\.\\d{4}") || !dateTo.matches("\\d{2}\\.\\d{4}")) {
                throw new IllegalArgumentException("Даты периодов должны быть в формате месяц.год.");
            }

            return new DateRangeInput(dateFrom, dateTo);
        }
    }

    private static final class EditableResultRow {
        private final HBox container = new HBox(10);
        private final TextField monthFromField = new TextField();
        private final TextField monthToField = new TextField();
        private final TextField resultField = new TextField();
        private final int columnIndex;

        private EditableResultRow(ExcelPreviewResult result, boolean showHeader) {
            this.columnIndex = result.getColumnIndex();
            monthFromField.setText(result.getMonthFrom());
            monthToField.setText(result.getMonthTo());
            monthFromField.setPromptText("from: месяц.год");
            monthToField.setPromptText("to: месяц.год");
            monthFromField.setEditable(false);
            monthToField.setEditable(false);
            monthFromField.setFocusTraversable(false);
            monthToField.setFocusTraversable(false);
            resultField.setEditable(false);
            resultField.setFocusTraversable(false);
            resultField.setMouseTransparent(true);
            resultField.setPrefWidth(120);
            HBox.setHgrow(monthFromField, Priority.ALWAYS);
            HBox.setHgrow(monthToField, Priority.ALWAYS);
            applyUpdatedResult(result);
            container.getChildren().addAll(
                    createColumn(showHeader ? "период ОТ" : "", monthFromField),
                    createColumn(showHeader ? "период ДО" : "", monthToField),
                    createColumn(showHeader ? "количество запросов" : "", resultField)
            );
        }

        private HBox container() {
            return container;
        }

        private ExcelPreviewResult toResult(String phrase) {
            String monthFrom = monthFromField.getText() == null ? "" : monthFromField.getText().trim();
            String monthTo = monthToField.getText() == null ? "" : monthToField.getText().trim();

            if (!monthFrom.matches("\\d{2}\\.\\d{4}") || !monthTo.matches("\\d{2}\\.\\d{4}")) {
                throw new IllegalArgumentException("Для phrase \"" + phrase + "\" даты должны быть в формате месяц.год.");
            }

            ExcelPreviewResult result = new ExcelPreviewResult();
            result.setColumnIndex(columnIndex);
            result.setMonthFrom(monthFrom);
            result.setMonthTo(monthTo);
            result.setDate("01." + monthFrom + " - 01." + monthTo);
            return result;
        }

        private String snapshot() {
            String monthFrom = monthFromField.getText() == null ? "" : monthFromField.getText().trim();
            String monthTo = monthToField.getText() == null ? "" : monthToField.getText().trim();
            return monthFrom + "->" + monthTo;
        }

        private void applyUpdatedResult(ExcelPreviewResult result) {
            monthFromField.setText(result.getMonthFrom());
            monthToField.setText(result.getMonthTo());
            String value = result.getCountSum() == null ? "нет" : String.valueOf(result.getCountSum());
            resultField.setText(value);
        }

        private static VBox createColumn(String labelText, javafx.scene.Node... controls) {
            VBox box = new VBox(4);
            Label label = new Label(labelText);
            label.setMinHeight(18);
            HBox controlsBox = new HBox(8);
            controlsBox.getChildren().addAll(controls);
            box.getChildren().addAll(label, controlsBox);
            HBox.setHgrow(box, Priority.ALWAYS);
            return box;
        }
    }

}
