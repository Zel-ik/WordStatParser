package org.paring.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.paring.model.DateRangeInput;
import org.paring.model.ExcelPreviewBlock;
import org.paring.model.ExcelPreviewResult;
import org.paring.model.ExcelPreviewSheet;
import org.paring.model.ExcelPreviewUniversity;
import org.paring.model.PhraseBlockInput;
import org.paring.model.WorkbookDraftRowInput;
import org.paring.service.RequestService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GUIController {
    private static final String[] MONTH_NAMES = {
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    };

    private final RequestService requestService;
    private final Label statusLabel = new Label("Заполните поля и нажмите \"Сформировать Excel\".");
    private final Button submitButton = new Button("Сформировать Excel");
    private final Label selectedExcelLabel = new Label("Excel файл не выбран");
    private final PasswordField authTokenField = new PasswordField();
    private final TabPane modeTabs = new TabPane();
    private final TabPane workbookTabs = new TabPane();
    private final List<DraftRowForm> draftRows = new ArrayList<>();
    private Path selectedExcelPath;

    public GUIController(RequestService requestService) {
        this.requestService = requestService;
    }

    public Parent createContent() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        VBox topContent = new VBox(16);
        topContent.setPadding(new Insets(0, 8, 12, 0));

        Label titleLabel = new Label("Загрузка данных Wordstat");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        HBox tokenBox = new HBox(12);
        Label tokenLabel = new Label("Токен Yandex");
        Button applyTokenButton = new Button("Применить токен");
        authTokenField.setPromptText("Вставьте новый токен для Wordstat API");
        HBox.setHgrow(authTokenField, Priority.ALWAYS);
        applyTokenButton.setOnAction(event -> applyAuthToken());
        tokenBox.getChildren().addAll(tokenLabel, authTokenField, applyTokenButton);

        statusLabel.setWrapText(true);

        topContent.getChildren().addAll(titleLabel, tokenBox, statusLabel);

        modeTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        modeTabs.getTabs().add(buildExistingWorkbookTab());
        modeTabs.getTabs().add(buildInputTab());

        root.setTop(topContent);
        root.setCenter(modeTabs);
        return root;
    }

    private void applyAuthToken() {
        try {
            requestService.updateAuthToken(authTokenField.getText());
            authTokenField.clear();
            statusLabel.setText("Токен Wordstat API обновлен для текущего запуска приложения.");
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private static void installCommitRefresh(TextField textField, Runnable refreshAction) {
        textField.setOnAction(event -> refreshAction.run());
        textField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused) {
                refreshAction.run();
            }
        });
    }

    private static Button createMonthPickerButton(TextField targetField, Runnable afterSelection) {
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

    private void rebuildTabs(List<ExcelPreviewSheet> previewSheets) {
        int selectedIndex = workbookTabs.getSelectionModel().getSelectedIndex();
        workbookTabs.getTabs().clear();

        if (previewSheets.isEmpty()) {
            VBox emptyState = new VBox(12);
            emptyState.setPadding(new Insets(14));
            emptyState.getChildren().add(new Label("Выберите Excel-файл, чтобы прочитать его листы."));
            Tab emptyTab = new Tab("Excel не выбран");
            emptyTab.setContent(emptyState);
            workbookTabs.getTabs().add(emptyTab);
        }

        for (ExcelPreviewSheet previewSheet : previewSheets) {
            Tab tab = new Tab(previewSheet.getSheetName());
            tab.setContent(createPreviewContent(previewSheet));
            workbookTabs.getTabs().add(tab);
        }

        if (!workbookTabs.getTabs().isEmpty()) {
            int safeIndex = Math.max(0, Math.min(selectedIndex, workbookTabs.getTabs().size() - 1));
            workbookTabs.getSelectionModel().select(safeIndex);
        }
    }

    private Tab buildExistingWorkbookTab() {
        BorderPane existingLayout = new BorderPane();
        existingLayout.setPadding(new Insets(10));

        HBox excelSelectionBox = new HBox(12);
        Button chooseExcelButton = new Button("Выбрать Excel");
        chooseExcelButton.setOnAction(event -> chooseExcelFile());
        Button addSheetButton = new Button("Добавить лист");
        addSheetButton.setOnAction(event -> addSheetToSelectedWorkbook());
        selectedExcelLabel.setWrapText(true);
        HBox.setHgrow(selectedExcelLabel, Priority.ALWAYS);
        excelSelectionBox.getChildren().addAll(chooseExcelButton, addSheetButton, selectedExcelLabel);

        workbookTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        rebuildTabs(List.of());

        existingLayout.setTop(excelSelectionBox);
        existingLayout.setCenter(workbookTabs);

        Tab existingTab = new Tab("Существующий Excel");
        existingTab.setContent(existingLayout);
        return existingTab;
    }

    private Tab buildInputTab() {
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
                showError("Сначала добавьте или выберите строку слева.");
                return;
            }
            selectedRow.addPhraseBlock();
        });

        HBox actionsRow = new HBox(12, addBlockButton, submitButton);
        submitButton.setOnAction(event -> handleSubmit());

        inputLayout.setLeft(leftPane);
        inputLayout.setCenter(centerPane);
        inputLayout.setBottom(actionsRow);

        Tab inputTab = new Tab("Новый файл");
        inputTab.setContent(inputLayout);
        return inputTab;
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
        VBox.setVgrow(universityList, Priority.ALWAYS);
        leftPane.getChildren().addAll(leftTitle, universityList);

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

        if (!previewSheet.getUniversities().isEmpty()) {
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
        PauseTransition universityAutoRefresh = new PauseTransition(Duration.millis(900));
        universityAutoRefresh.setOnFinished(event -> {
            for (BlockEditorState blockEditorState : blockEditorStates) {
                blockEditorState.refresh(false);
            }
        });
        installCommitRefresh(fullNameField, universityAutoRefresh::playFromStart);

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
            installCommitRefresh(phraseField, blockEditorState::scheduleAutoRefresh);
            blockBox.getChildren().addAll(phraseLabel, phraseField, refillButton);

            for (ExcelPreviewResult result : block.getResults()) {
                EditableResultRow editableRow = new EditableResultRow(result);
                editableRows.add(editableRow);
                editableRow.installAutoRefresh(blockEditorState::scheduleAutoRefresh);
                blockBox.getChildren().add(editableRow.container());
            }

            universityCard.getChildren().add(blockBox);
        }

        ScrollPane scrollPane = new ScrollPane(universityCard);
        scrollPane.setFitToWidth(true);
        return scrollPane;
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

        File selectedFile = fileChooser.showOpenDialog(submitButton.getScene() == null ? null : submitButton.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        selectedExcelPath = selectedFile.toPath();
        selectedExcelLabel.setText(selectedFile.getAbsolutePath());
        statusLabel.setText("Читаем страницы выбранного Excel...");
        reloadWorkbookPreview();
    }

    private void addSheetToSelectedWorkbook() {
        if (selectedExcelPath == null) {
            showError("Сначала выберите Excel файл.");
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
                showError(exception.getMessage());
            }
        });
    }

    private void handleSubmit() {
        List<WorkbookDraftRowInput> rows = new ArrayList<>();
        try {
            for (DraftRowForm draftRow : draftRows) {
                rows.add(draftRow.toModel());
            }
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
            return;
        }
        if (rows.isEmpty()) {
            showError("Добавьте хотя бы одну строку для нового файла.");
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
            statusLabel.setText("Готово. Новый файл сохранен: " + outputPath.toAbsolutePath());
        });

        task.setOnFailed(event -> {
            submitButton.setDisable(false);
            Throwable throwable = task.getException();
            statusLabel.setText("Ошибка при выполнении запросов.");
            showError(throwable == null ? "Неизвестная ошибка." : throwable.getMessage());
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
            showError("Файл с таким именем уже существует. Выберите другое имя или другую папку.");
            return null;
        }

        return outputPath;
    }

    private DraftRowForm addDraftRow() {
        DraftRowForm row = new DraftRowForm(draftRows.size() + 1);
        draftRows.add(row);
        return row;
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
                editedUniversity.setRowIndex(university.getRowIndex());
                editedUniversity.setFullNameColumnIndex(university.getFullNameColumnIndex());
                editedUniversity.setUniversityName(universityName);
                return requestService.refillBlock(selectedExcelPath, editedUniversity, editedBlock);
            }
        };

        task.setOnSucceeded(event -> {
            refillButton.setDisable(false);
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
            statusLabel.setText("Не удалось обновить блок.");
            Throwable throwable = task.getException();
            showError(throwable == null ? "Неизвестная ошибка." : throwable.getMessage());
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
        private final PauseTransition autoRefreshDelay = new PauseTransition(Duration.millis(900));
        private boolean refreshInFlight;
        private boolean refreshQueued;
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
            this.autoRefreshDelay.setOnFinished(event -> refresh(false));
        }

        private void scheduleAutoRefresh() {
            autoRefreshDelay.playFromStart();
        }

        private void refresh(boolean force) {
            if (refreshInFlight) {
                refreshQueued = true;
                return;
            }

            String currentSnapshot = captureSnapshot();
            if (!force && currentSnapshot.equals(lastSubmittedSnapshot)) {
                return;
            }

            autoRefreshDelay.stop();
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
                        if (refreshQueued) {
                            refreshQueued = false;
                            if (!captureSnapshot().equals(lastSubmittedSnapshot)) {
                                scheduleAutoRefresh();
                            }
                        }
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
            rebuildTabs(task.getValue());
            statusLabel.setText("Страницы Excel загружены.");
        });

        task.setOnFailed(event -> {
            statusLabel.setText("Не удалось загрузить Excel.");
            Throwable throwable = task.getException();
            showError(throwable == null ? "Неизвестная ошибка при чтении Excel." : throwable.getMessage());
        });

        Thread thread = new Thread(task, "excel-preview-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
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

            Button fromPickerButton = createMonthPickerButton(dateFromField, () -> {});
            Button toPickerButton = createMonthPickerButton(dateToField, () -> {});

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

    private static final class EditableResultRow {
        private final HBox container = new HBox(10);
        private final TextField monthFromField = new TextField();
        private final TextField monthToField = new TextField();
        private final Button monthFromPickerButton = new Button("Выбрать");
        private final Button monthToPickerButton = new Button("Выбрать");
        private final Label resultLabel = new Label();
        private final int columnIndex;

        private EditableResultRow(ExcelPreviewResult result) {
            this.columnIndex = result.getColumnIndex();
            monthFromField.setText(result.getMonthFrom());
            monthToField.setText(result.getMonthTo());
            monthFromField.setPromptText("from: месяц.год");
            monthToField.setPromptText("to: месяц.год");
            HBox.setHgrow(monthFromField, Priority.ALWAYS);
            HBox.setHgrow(monthToField, Priority.ALWAYS);
            applyUpdatedResult(result);
            container.getChildren().addAll(
                    new Label("Период"),
                    monthFromField,
                    monthFromPickerButton,
                    monthToField,
                    monthToPickerButton,
                    resultLabel
            );
        }

        private HBox container() {
            return container;
        }

        private void installAutoRefresh(Runnable onChangeFinished) {
            installCommitRefresh(monthFromField, onChangeFinished);
            installCommitRefresh(monthToField, onChangeFinished);
            monthFromPickerButton.setOnAction(event -> showMonthPicker(monthFromField, onChangeFinished));
            monthToPickerButton.setOnAction(event -> showMonthPicker(monthToField, onChangeFinished));
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
            resultLabel.setText(result.getDate() + " -> " + value);
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

    private record MonthOption(int value, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
