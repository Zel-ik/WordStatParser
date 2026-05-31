package org.paring.controller;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.paring.model.ExcelPreviewSheet;
import org.paring.model.ExcelPreviewUniversity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class ExistingWorkbookController {
    private final Function<ExcelPreviewSheet, Parent> previewContentFactory;
    private final Runnable addSheetAction;
    private final Runnable setPeriodsAction;
    private final Label selectedExcelLabel = new Label("Excel файл не выбран");
    private final TabPane workbookTabs = new TabPane();
    private List<ExcelPreviewSheet> currentPreviewSheets = new ArrayList<>();
    private String rowToSelectAfterReloadSheetName;
    private Integer rowToSelectAfterReloadIndex;

    ExistingWorkbookController(Function<ExcelPreviewSheet, Parent> previewContentFactory,
                               Runnable addSheetAction,
                               Runnable setPeriodsAction) {
        this.previewContentFactory = previewContentFactory;
        this.addSheetAction = addSheetAction;
        this.setPeriodsAction = setPeriodsAction;
    }

    Parent createContent() {
        BorderPane existingLayout = new BorderPane();
        existingLayout.setPadding(new Insets(10));

        HBox excelSelectionBox = new HBox(12);
        Button addSheetButton = new Button("Добавить лист");
        addSheetButton.setOnAction(event -> addSheetAction.run());
        Button setPeriodsButton = new Button("Задать периоды дат");
        setPeriodsButton.setOnAction(event -> setPeriodsAction.run());
        selectedExcelLabel.setWrapText(true);
        HBox.setHgrow(selectedExcelLabel, Priority.ALWAYS);
        excelSelectionBox.getChildren().addAll(addSheetButton, setPeriodsButton, selectedExcelLabel);

        workbookTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        rebuildTabs(List.of());

        existingLayout.setTop(excelSelectionBox);
        existingLayout.setCenter(workbookTabs);

        return existingLayout;
    }

    void rebuildTabs(List<ExcelPreviewSheet> previewSheets) {
        currentPreviewSheets = new ArrayList<>(previewSheets);
        int selectedIndex = workbookTabs.getSelectionModel().getSelectedIndex();
        workbookTabs.getTabs().clear();

        if (previewSheets.isEmpty()) {
            javafx.scene.layout.VBox emptyState = new javafx.scene.layout.VBox(12);
            emptyState.setPadding(new Insets(14));
            emptyState.getChildren().add(new Label("Выберите Excel-файл, чтобы прочитать его листы."));
            Tab emptyTab = new Tab("Excel не выбран");
            emptyTab.setContent(emptyState);
            workbookTabs.getTabs().add(emptyTab);
        }

        for (ExcelPreviewSheet previewSheet : previewSheets) {
            Tab tab = new Tab(previewSheet.getSheetName());
            tab.setContent(previewContentFactory.apply(previewSheet));
            workbookTabs.getTabs().add(tab);
        }

        if (!workbookTabs.getTabs().isEmpty()) {
            int safeIndex = Math.max(0, Math.min(selectedIndex, workbookTabs.getTabs().size() - 1));
            workbookTabs.getSelectionModel().select(safeIndex);
        }
    }

    void setSelectedExcelPath(String path) {
        selectedExcelLabel.setText(path);
    }

    ExcelPreviewSheet getSelectedPreviewSheet() {
        Tab selectedTab = workbookTabs.getSelectionModel().getSelectedItem();
        if (selectedTab == null) {
            return null;
        }

        String selectedSheetName = selectedTab.getText();
        for (ExcelPreviewSheet previewSheet : currentPreviewSheets) {
            if (previewSheet.getSheetName().equals(selectedSheetName)) {
                return previewSheet;
            }
        }
        return null;
    }

    ExcelPreviewUniversity findUniversityToSelectAfterReload(ExcelPreviewSheet previewSheet) {
        if (rowToSelectAfterReloadSheetName == null || rowToSelectAfterReloadIndex == null) {
            return null;
        }
        if (!rowToSelectAfterReloadSheetName.equals(previewSheet.getSheetName())) {
            return null;
        }

        for (ExcelPreviewUniversity university : previewSheet.getUniversities()) {
            if (university.getRowIndex() == rowToSelectAfterReloadIndex) {
                return university;
            }
        }
        return null;
    }

    void rememberRowSelectionAfterReload(ExcelPreviewUniversity university) {
        if (university == null) {
            return;
        }
        rowToSelectAfterReloadSheetName = university.getSheetName();
        rowToSelectAfterReloadIndex = university.getRowIndex();
    }

    void clearRowSelectionRestoreRequest() {
        rowToSelectAfterReloadSheetName = null;
        rowToSelectAfterReloadIndex = null;
    }
}
