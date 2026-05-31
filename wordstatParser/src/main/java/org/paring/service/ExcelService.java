package org.paring.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
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

            for (int resultIndex = 0; resultIndex < DEFAULT_PERIOD_COUNT; resultIndex++) {
                headerRow.createCell(columnIndex).setCellValue(resolveFullNamePeriodHeader(rows, resultIndex));
                columnIndex++;
            }

            for (int blockIndex = 0; blockIndex < maxBlocks; blockIndex++) {
                headerRow.createCell(columnIndex).setCellValue(buildPhraseHeader(blockIndex + 1));
                headerRow.getCell(columnIndex).setCellStyle(boldStyle);
                columnIndex++;

                for (int resultIndex = 0; resultIndex < DEFAULT_PERIOD_COUNT; resultIndex++) {
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
                for (int resultIndex = 0; resultIndex < DEFAULT_PERIOD_COUNT; resultIndex++) {
                    if (resultIndex < workbookRow.getFullNameResults().size()) {
                        ResponseToSaveToExcelDTO result = workbookRow.getFullNameResults().get(resultIndex);
                        dataRow.createCell(dataColumnIndex).setCellValue(
                                result.getCountSum() == null ? "нет" : String.valueOf(result.getCountSum())
                        );
                    }
                    dataColumnIndex++;
                }

                for (int blockIndex = 0; blockIndex < maxBlocks; blockIndex++) {
                    if (blockIndex < workbookRow.getBlocks().size()) {
                        WorkbookBlockResult block = workbookRow.getBlocks().get(blockIndex);
                        dataRow.createCell(dataColumnIndex).setCellValue(block.getPhrase());
                        dataColumnIndex++;

                        for (int resultIndex = 0; resultIndex < DEFAULT_PERIOD_COUNT; resultIndex++) {
                            if (resultIndex < block.getResults().size()) {
                                ResponseToSaveToExcelDTO result = block.getResults().get(resultIndex);
                                dataRow.createCell(dataColumnIndex).setCellValue(
                                        result.getCountSum() == null ? "нет" : String.valueOf(result.getCountSum())
                                );
                            }
                            dataColumnIndex++;
                        }
                    } else {
                        dataColumnIndex += DEFAULT_PERIOD_COUNT + 1;
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

    public void updateSheetPeriodHeaders(Path path, String sheetName, List<String> dateRanges) {
        if (dateRanges.isEmpty()) {
            return;
        }

        try {
            Workbook workbook;
            try (InputStream inputStream = Files.newInputStream(path)) {
                workbook = WorkbookFactory.create(inputStream);
            }

            try (workbook) {
                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    throw new IllegalStateException("Не удалось найти лист \"" + sheetName + "\" для обновления периодов.");
                }

                Row headerRow = findTemplateHeaderRow(sheet);
                if (headerRow == null) {
                    throw new IllegalStateException("Не удалось найти строку заголовков на листе \"" + sheetName + "\".");
                }

                for (int columnIndex = 0; columnIndex < headerRow.getLastCellNum(); columnIndex++) {
                    String header = new DataFormatter().formatCellValue(headerRow.getCell(columnIndex)).toLowerCase();
                    if (!header.contains("кол-во")) {
                        continue;
                    }

                    int periodIndex = resolvePeriodIndex(headerRow, columnIndex);
                    if (periodIndex >= 0 && periodIndex < dateRanges.size()) {
                        headerRow.createCell(columnIndex).setCellValue(buildPeriodHeader(dateRanges.get(periodIndex)));
                    }
                }

                try (FileOutputStream outputStream = new FileOutputStream(path.toFile())) {
                    workbook.write(outputStream);
                }
            }
        } catch (IOException e) {
            log.error("Ошибка при обновлении периодов листа\n" + e.getMessage());
            throw new IllegalStateException("Не удалось обновить периоды листа: " + e.getMessage(), e);
        }
    }

    private Row findTemplateHeaderRow(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        int maxRow = Math.min(sheet.getLastRowNum(), 10);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            boolean hasFullName = false;
            boolean hasPhrase = false;
            for (org.apache.poi.ss.usermodel.Cell cell : row) {
                String value = formatter.formatCellValue(cell).toLowerCase();
                if (value.contains("полное")) {
                    hasFullName = true;
                }
                if (value.contains("сокращ")) {
                    hasPhrase = true;
                }
            }

            if (hasFullName && hasPhrase) {
                return row;
            }
        }
        return null;
    }

    private int resolvePeriodIndex(Row headerRow, int columnIndex) {
        int resultIndex = 0;
        DataFormatter formatter = new DataFormatter();
        for (int currentColumn = 0; currentColumn <= columnIndex; currentColumn++) {
            String header = formatter.formatCellValue(headerRow.getCell(currentColumn)).toLowerCase();
            if (header.contains("сокращ")) {
                resultIndex = 0;
                continue;
            }
            if (header.contains("кол-во")) {
                if (currentColumn == columnIndex) {
                    return resultIndex;
                }
                resultIndex++;
            }
        }
        return -1;
    }

    private void createTemplateHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        increaseRowHeight(headerRow);
        Workbook workbook = sheet.getWorkbook();
        CellStyle boldStyle = createBoldCellStyle(workbook);

        headerRow.createCell(0).setCellValue("Полное название учебного заведения");
        headerRow.getCell(0).setCellStyle(boldStyle);

        for (int i = 0; i < DEFAULT_PERIOD_COUNT; i++) {
            headerRow.createCell(i + 1).setCellValue("кол-во за период");
        }

        int phraseColumnIndex = DEFAULT_PERIOD_COUNT + 1;
        headerRow.createCell(phraseColumnIndex).setCellValue(buildPhraseHeader(1));
        headerRow.getCell(phraseColumnIndex).setCellStyle(boldStyle);

        for (int i = 0; i < DEFAULT_PERIOD_COUNT; i++) {
            headerRow.createCell(phraseColumnIndex + i + 1).setCellValue("кол-во за период");
        }

        for (int i = 0; i < DEFAULT_PERIOD_COUNT * 2 + 2; i++) {
            sheet.autoSizeColumn(i);
            increaseColumnWidth(sheet, i, isPhraseLikeColumn(i) || i == 0 ? 1.8 : 1.3);
        }
    }

    private boolean isUniversityNameColumn(int columnIndex, int maxBlocks) {
        if (columnIndex == 0) {
            return true;
        }

        int currentColumn = DEFAULT_PERIOD_COUNT + 1;
        for (int blockIndex = 0; blockIndex < maxBlocks; blockIndex++) {
            if (currentColumn == columnIndex) {
                return true;
            }
            currentColumn += DEFAULT_PERIOD_COUNT + 1;
        }
        return false;
    }

    private boolean isPhraseLikeColumn(int columnIndex) {
        return columnIndex >= DEFAULT_PERIOD_COUNT + 1
                && (columnIndex - (DEFAULT_PERIOD_COUNT + 1)) % (DEFAULT_PERIOD_COUNT + 1) == 0;
    }

    private String resolveFullNamePeriodHeader(List<WorkbookRowResult> rows, int resultIndex) {
        for (WorkbookRowResult row : rows) {
            if (resultIndex < row.getFullNameResults().size()) {
                return buildPeriodHeader(row.getFullNameResults().get(resultIndex).getDate());
            }
        }
        return "кол-во за период";
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
