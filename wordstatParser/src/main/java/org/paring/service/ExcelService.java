package org.paring.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.WorkbookUtil;
import org.paring.model.ExcelPreviewBlock;
import org.paring.model.ExcelPreviewResult;
import org.paring.model.ExcelPreviewUniversity;
import org.paring.model.ResponseToSaveToExcelDTO;
import org.paring.model.WorkbookBlockResult;
import org.paring.model.WorkbookRowResult;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class ExcelService {
    private static final int DEFAULT_PERIOD_COUNT = 3;

    public void createBook(List<WorkbookRowResult> rows, Path outputPath) {
        try (Workbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Данные");
            Row headerRow = sheet.createRow(0);
            increaseRowHeight(headerRow);

            CellStyle boldStyle = createBoldCellStyle(workbook);
            headerRow.createCell(0).setCellValue("Полное название учебного заведения");
            headerRow.getCell(0).setCellStyle(boldStyle);

            int maxBlocks = rows.stream().mapToInt(row -> row.getBlocks().size()).max().orElse(0);
            int columnIndex = 1;

            for (int blockIndex = 0; blockIndex < maxBlocks; blockIndex++) {
                headerRow.createCell(columnIndex).setCellValue(buildPhraseHeader(blockIndex + 1));
                headerRow.getCell(columnIndex).setCellStyle(boldStyle);
                columnIndex++;

                for (int resultIndex = 0; resultIndex < 3; resultIndex++) {
                    headerRow.createCell(columnIndex).setCellValue(resolvePeriodHeader(rows, blockIndex, resultIndex));
                    columnIndex++;
                }
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                WorkbookRowResult workbookRow = rows.get(rowIndex);
                Row dataRow = sheet.createRow(rowIndex + 1);
                increaseRowHeight(dataRow);
                dataRow.createCell(0).setCellValue(workbookRow.getUniversityName());

                int dataColumnIndex = 1;
                for (int blockIndex = 0; blockIndex < maxBlocks; blockIndex++) {
                    if (blockIndex < workbookRow.getBlocks().size()) {
                        WorkbookBlockResult block = workbookRow.getBlocks().get(blockIndex);
                        dataRow.createCell(dataColumnIndex).setCellValue(block.getPhrase());
                        dataColumnIndex++;

                        for (int resultIndex = 0; resultIndex < 3; resultIndex++) {
                            if (resultIndex < block.getResults().size()) {
                                ResponseToSaveToExcelDTO result = block.getResults().get(resultIndex);
                                dataRow.createCell(dataColumnIndex).setCellValue(
                                        result.getCountSum() == null ? "нет" : String.valueOf(result.getCountSum())
                                );
                            }
                            dataColumnIndex++;
                        }
                    } else {
                        dataColumnIndex += 4;
                    }
                }
            }

            for (int i = 0; i < columnIndex; i++) {
                sheet.autoSizeColumn(i);
                increaseColumnWidth(sheet, i, isUniversityNameColumn(i, maxBlocks) ? 1.8 : 1.3);
            }

            try (FileOutputStream fileOut = new FileOutputStream(outputPath.toFile())) {
                workbook.write(fileOut);
            } catch (IOException e) {
                log.error("Ошибка при попытке сохранить excel файл\n" + e.getMessage());
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            log.error("Ошибка при попытке создать excel файл\n" + e.getMessage());
        }
    }

    public String addSheet(Path path, String requestedSheetName) {
        String sheetName = requestedSheetName == null ? "" : requestedSheetName.trim();
        if (sheetName.isBlank()) {
            throw new IllegalArgumentException("Название листа не должно быть пустым.");
        }

        try {
            Workbook workbook;
            try (InputStream inputStream = Files.newInputStream(path)) {
                workbook = WorkbookFactory.create(inputStream);
            }

            try (workbook) {
                String safeSheetName = WorkbookUtil.createSafeSheetName(sheetName);
                if (workbook.getSheet(safeSheetName) != null) {
                    throw new IllegalArgumentException("Лист с названием \"" + safeSheetName + "\" уже существует.");
                }

                Sheet sheet = workbook.createSheet(safeSheetName);
                createTemplateHeader(sheet);

                try (FileOutputStream outputStream = new FileOutputStream(path.toFile())) {
                    workbook.write(outputStream);
                }
                return safeSheetName;
            }
        } catch (IOException e) {
            log.error("Ошибка при добавлении листа в excel файл\n" + e.getMessage());
            throw new IllegalStateException("Не удалось добавить лист в выбранный Excel: " + e.getMessage(), e);
        }
    }

    private void createTemplateHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        increaseRowHeight(headerRow);
        Workbook workbook = sheet.getWorkbook();
        CellStyle boldStyle = createBoldCellStyle(workbook);

        headerRow.createCell(0).setCellValue("Полное название учебного заведения");
        headerRow.getCell(0).setCellStyle(boldStyle);

        headerRow.createCell(1).setCellValue(buildPhraseHeader(1));
        headerRow.getCell(1).setCellStyle(boldStyle);

        for (int i = 0; i < DEFAULT_PERIOD_COUNT; i++) {
            headerRow.createCell(i + 2).setCellValue("кол-во за период");
        }

        for (int i = 0; i < DEFAULT_PERIOD_COUNT + 2; i++) {
            sheet.autoSizeColumn(i);
            increaseColumnWidth(sheet, i, i == 0 || i == 1 ? 1.8 : 1.3);
        }
    }

    private boolean isUniversityNameColumn(int columnIndex, int maxBlocks) {
        if (columnIndex == 0) {
            return true;
        }

        int currentColumn = 1;
        for (int blockIndex = 0; blockIndex < maxBlocks; blockIndex++) {
            if (currentColumn == columnIndex) {
                return true;
            }
            currentColumn += 4;
        }
        return false;
    }

    private String resolvePeriodHeader(List<WorkbookRowResult> rows, int blockIndex, int resultIndex) {
        for (WorkbookRowResult row : rows) {
            if (blockIndex >= row.getBlocks().size()) {
                continue;
            }

            WorkbookBlockResult block = row.getBlocks().get(blockIndex);
            if (resultIndex < block.getResults().size()) {
                return buildPeriodHeader(block.getResults().get(resultIndex).getDate());
            }
        }
        return "кол-во за период";
    }

    private String buildPhraseHeader(int phraseIndex) {
        return "сокращенное " + phraseIndex;
    }

    private void increaseColumnWidth(Sheet sheet, int columnIndex, double multiplier) {
        int currentWidth = sheet.getColumnWidth(columnIndex);
        int increasedWidth = (int) Math.round(currentWidth * multiplier);
        sheet.setColumnWidth(columnIndex, Math.min(increasedWidth, 255 * 256));
    }

    private void increaseRowHeight(Row row) {
        row.setHeightInPoints((float) (row.getSheet().getDefaultRowHeightInPoints() * 1.15));
    }

    private CellStyle createBoldCellStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFont(font);
        return cellStyle;
    }

    public void updateBlockInWorkbook(Path path,
                                      ExcelPreviewUniversity university,
                                      ExcelPreviewBlock block,
                                      List<ExcelPreviewResult> updatedResults) {
        try {
            Workbook workbook;
            try (InputStream inputStream = Files.newInputStream(path)) {
                workbook = WorkbookFactory.create(inputStream);
            }

            try (workbook) {
                Sheet sheet = workbook.getSheet(block.getSheetName());
                if (sheet == null) {
                    throw new IllegalStateException("Не удалось найти лист \"" + block.getSheetName() + "\" для обновления.");
                }

                Row row = sheet.getRow(university.getRowIndex());
                if (row == null) {
                    throw new IllegalStateException("Не удалось найти строку блока для обновления в Excel.");
                }

                Row headerRow = sheet.getRow(block.getHeaderRowIndex());
                if (headerRow == null) {
                    throw new IllegalStateException("Не удалось найти строку заголовков периода для обновления в Excel.");
                }

                row.createCell(university.getFullNameColumnIndex()).setCellValue(university.getUniversityName());
                row.createCell(block.getPhraseColumnIndex()).setCellValue(block.getPhrase());

                for (ExcelPreviewResult result : updatedResults) {
                    headerRow.createCell(result.getColumnIndex()).setCellValue(buildPeriodHeader(result.getDate()));
                    row.createCell(result.getColumnIndex()).setCellValue(
                            result.getCountSum() == null ? "нет" : String.valueOf(result.getCountSum())
                    );
                }

                try (FileOutputStream outputStream = new FileOutputStream(path.toFile())) {
                    workbook.write(outputStream);
                }
            }
        } catch (IOException e) {
            log.error("Ошибка при обновлении выбранного excel файла\n" + e.getMessage());
            throw new IllegalStateException("Не удалось обновить выбранный Excel: " + e.getMessage(), e);
        }
    }

    private String buildPeriodHeader(String dateRange) {
        return "кол-во за период " + dateRange.replace(" - ", " – ");
    }
}
