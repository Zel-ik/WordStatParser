package org.paring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.paring.config.AccessConfigs;
import org.paring.enums.Device;
import org.paring.enums.Period;
import org.paring.model.CountOfRequest;
import org.paring.model.DateRangeInput;
import org.paring.model.ExcelPreviewBlock;
import org.paring.model.ExcelPreviewResult;
import org.paring.model.ExcelPreviewSheet;
import org.paring.model.ExcelPreviewUniversity;
import org.paring.model.PhraseBlockInput;
import org.paring.model.ResponseToSaveToExcelDTO;
import org.paring.model.UniRequest;
import org.paring.model.UniResponse;
import org.paring.model.WorkbookBlockResult;
import org.paring.model.WorkbookDraftRowInput;
import org.paring.model.WorkbookRowResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class RequestService {

    private static final DateTimeFormatter INPUT_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM.yyyy");
    private static final DateTimeFormatter OUTPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern HEADER_DATE_RANGE_PATTERN = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{2,4}).*?(\\d{2}\\.\\d{2}\\.\\d{2,4})");

    private final AccessConfigs accessConfigs;
    private final ExcelService excelService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RequestService(AccessConfigs accessConfigs, ExcelService excelService) {
        this.accessConfigs = accessConfigs;
        this.excelService = excelService;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void updateAuthToken(String authToken) {
        String normalizedToken = authToken == null ? "" : authToken.trim();
        if (normalizedToken.isBlank()) {
            throw new IllegalArgumentException("Токен не должен быть пустым.");
        }
        accessConfigs.setAuthToken(normalizedToken);
    }

    public void createWorkbook(List<WorkbookDraftRowInput> rows, Path outputPath) {
        try {
            List<WorkbookRowResult> workbookRows = new ArrayList<>();

            for (WorkbookDraftRowInput rowInput : rows) {
                WorkbookRowResult workbookRow = new WorkbookRowResult();
                workbookRow.setUniversityName(rowInput.getUniversityName());

                for (PhraseBlockInput block : rowInput.getBlocks()) {
                    WorkbookBlockResult blockResult = new WorkbookBlockResult();
                    blockResult.setPhrase(block.getPhrase());

                    for (DateRangeInput dateRange : block.getDateRanges()) {
                        UniResponse uniResponse = sendWordStatRequest(block.getPhrase(), dateRange);
                        blockResult.getResults().add(
                                buildResponseDto(rowInput.getUniversityName(), block.getPhrase(), dateRange, uniResponse)
                        );
                    }

                    workbookRow.getBlocks().add(blockResult);
                }

                workbookRows.add(workbookRow);
            }

            excelService.createBook(workbookRows, outputPath);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при парсинге", e);
            throw new IllegalStateException("Ошибка при парсинге запроса или ответа.", e);
        } catch (Exception e) {
            log.error("Ошибка при попытке сделать запрос", e);
            throw new IllegalStateException("Не удалось получить данные Wordstat: " + e.getMessage(), e);
        }
    }

    public List<ExcelPreviewSheet> readWorkbookPreview(Path path) {
        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(path))) {
            List<ExcelPreviewSheet> previewSheets = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                previewSheets.add(parseSheet(workbook.getSheetAt(i)));
            }
            return previewSheets;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать Excel файл: " + e.getMessage(), e);
        }
    }

    public String addSheetToWorkbook(Path path, String sheetName) {
        return excelService.addSheet(path, sheetName);
    }

    public List<ExcelPreviewResult> refillBlock(Path path, ExcelPreviewUniversity university, ExcelPreviewBlock block) {
        try {
            List<ExcelPreviewResult> updatedResults = new ArrayList<>();

            for (ExcelPreviewResult previewResult : block.getResults()) {
                DateRangeInput dateRange = new DateRangeInput(previewResult.getMonthFrom(), previewResult.getMonthTo());
                UniResponse uniResponse = sendWordStatRequest(block.getPhrase(), dateRange);

                ExcelPreviewResult updatedResult = new ExcelPreviewResult();
                updatedResult.setColumnIndex(previewResult.getColumnIndex());
                updatedResult.setMonthFrom(previewResult.getMonthFrom());
                updatedResult.setMonthTo(previewResult.getMonthTo());
                updatedResult.setDate(
                        normalizeToFirstDayOfMonth(previewResult.getMonthFrom()) + " - " +
                                normalizeToLastDayOfMonth(previewResult.getMonthTo())
                );
                updatedResult.setCountSum(uniResponse.getDynamics().stream().mapToInt(CountOfRequest::getCount).sum());
                updatedResults.add(updatedResult);
            }

            excelService.updateBlockInWorkbook(path, university, block, updatedResults);
            return updatedResults;
        } catch (JsonProcessingException e) {
            log.error("Ошибка при парсинге", e);
            throw new IllegalStateException("Ошибка при парсинге запроса или ответа.", e);
        } catch (Exception e) {
            log.error("Ошибка при попытке переотправить блок", e);
            throw new IllegalStateException("Не удалось переотправить блок: " + e.getMessage(), e);
        }
    }

    private UniRequest createRequest(String phrase, DateRangeInput dateRange) {
        String normalizedDateFrom = normalizeToFirstDayOfMonth(dateRange.getDateFrom());
        String normalizedDateTo = normalizeToLastDayOfMonth(dateRange.getDateTo());

        UniRequest uniRequest = new UniRequest();
        uniRequest.setPhrase(normalizePhraseForWordStat(phrase));
        uniRequest.getDevices().add(Device.ALL.getType());
        uniRequest.setPeriod(Period.MONTHLY.getName());
        uniRequest.setFromDate(convertDate(normalizedDateFrom));
        uniRequest.setToDate(convertDate(normalizedDateTo));
        return uniRequest;
    }

    private String normalizePhraseForWordStat(String phrase) {
        if (phrase == null) {
            return "";
        }
        return phrase
                .replace("\"", "")
                .replace("«", "")
                .replace("»", "")
                .trim();
    }

    private UniResponse sendWordStatRequest(String phrase, DateRangeInput dateRange) throws IOException, InterruptedException {
        UniRequest uniRequest = createRequest(phrase, dateRange);
        String jsonBody = objectMapper.writeValueAsString(uniRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https:/" + accessConfigs.getYandexUri() + accessConfigs.getDynamicsUri()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessConfigs.getAuthToken())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), UniResponse.class);
    }

    private ResponseToSaveToExcelDTO buildResponseDto(String universityName,
                                                      String phrase,
                                                      DateRangeInput dateRange,
                                                      UniResponse uniResponse) {
        String normalizedDateFrom = normalizeToFirstDayOfMonth(dateRange.getDateFrom());
        String normalizedDateTo = normalizeToLastDayOfMonth(dateRange.getDateTo());

        ResponseToSaveToExcelDTO responseToExcelDTO = new ResponseToSaveToExcelDTO();
        responseToExcelDTO.setUniversityName(universityName);
        responseToExcelDTO.setPhrase(phrase);
        responseToExcelDTO.setDate(normalizedDateFrom + " - " + normalizedDateTo);
        responseToExcelDTO.setCountSum(uniResponse.getDynamics().stream().mapToInt(CountOfRequest::getCount).sum());
        return responseToExcelDTO;
    }

    private ExcelPreviewSheet parseSheet(Sheet sheet) {
        ExcelPreviewSheet previewSheet = new ExcelPreviewSheet();
        previewSheet.setSheetName(sheet.getSheetName());

        int headerRowIndex = findHeaderRow(sheet);
        if (headerRowIndex < 0) {
            return previewSheet;
        }

        Row headerRow = sheet.getRow(headerRowIndex);
        Integer fullNameColumn = findColumnIndex(headerRow, "полное");
        if (fullNameColumn == null) {
            return previewSheet;
        }

        Map<Integer, String> phraseColumns = new LinkedHashMap<>();
        Map<Integer, String> resultColumns = new LinkedHashMap<>();
        collectColumns(headerRow, phraseColumns, resultColumns);

        DataFormatter formatter = new DataFormatter();
        for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String universityName = formatter.formatCellValue(row.getCell(fullNameColumn)).trim();
            if (universityName.isBlank()) {
                continue;
            }

            ExcelPreviewUniversity university = new ExcelPreviewUniversity();
            university.setRowIndex(rowIndex);
            university.setFullNameColumnIndex(fullNameColumn);
            university.setUniversityName(universityName);

            for (Integer phraseColumn : phraseColumns.keySet()) {
                String phrase = formatter.formatCellValue(row.getCell(phraseColumn)).trim();
                if (phrase.isBlank()) {
                    continue;
                }

                ExcelPreviewBlock block = new ExcelPreviewBlock();
                block.setSheetName(sheet.getSheetName());
                block.setHeaderRowIndex(headerRowIndex);
                block.setRowIndex(rowIndex);
                block.setPhraseColumnIndex(phraseColumn);
                block.setPhrase(phrase);

                Integer nextPhraseColumn = getNextPhraseColumn(phraseColumns, phraseColumn);
                for (Map.Entry<Integer, String> resultEntry : resultColumns.entrySet()) {
                    int resultColumn = resultEntry.getKey();
                    if (resultColumn <= phraseColumn) {
                        continue;
                    }
                    if (nextPhraseColumn != null && resultColumn >= nextPhraseColumn) {
                        break;
                    }

                    String rawValue = formatter.formatCellValue(row.getCell(resultColumn)).trim();
                    ExcelPreviewResult previewResult = new ExcelPreviewResult();
                    previewResult.setColumnIndex(resultColumn);
                    previewResult.setDate(resultEntry.getValue());
                    previewResult.setMonthFrom(extractMonthFromDateRange(resultEntry.getValue(), true));
                    previewResult.setMonthTo(extractMonthFromDateRange(resultEntry.getValue(), false));
                    previewResult.setCountSum(parseInteger(rawValue));
                    block.getResults().add(previewResult);
                }

                if (!block.getResults().isEmpty()) {
                    university.getBlocks().add(block);
                }
            }

            if (!university.getBlocks().isEmpty()) {
                previewSheet.getUniversities().add(university);
            }
        }

        return previewSheet;
    }

    private int findHeaderRow(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        int maxRow = Math.min(sheet.getLastRowNum(), 10);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            boolean hasFullName = false;
            boolean hasPhrase = false;
            for (Cell cell : row) {
                String value = formatter.formatCellValue(cell).toLowerCase();
                if (value.contains("полное")) {
                    hasFullName = true;
                }
                if (value.contains("сокращ")) {
                    hasPhrase = true;
                }
            }

            if (hasFullName && hasPhrase) {
                return rowIndex;
            }
        }
        return -1;
    }

    private Integer findColumnIndex(Row row, String marker) {
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : row) {
            String value = formatter.formatCellValue(cell).toLowerCase();
            if (value.contains(marker)) {
                return cell.getColumnIndex();
            }
        }
        return null;
    }

    private void collectColumns(Row headerRow, Map<Integer, String> phraseColumns, Map<Integer, String> resultColumns) {
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : headerRow) {
            String value = formatter.formatCellValue(cell).trim();
            String lowerValue = value.toLowerCase();
            if (lowerValue.contains("сокращ")) {
                phraseColumns.put(cell.getColumnIndex(), value);
            }
            if (lowerValue.contains("кол-во")) {
                resultColumns.put(cell.getColumnIndex(), extractDateRange(value));
            }
        }
    }

    private Integer getNextPhraseColumn(Map<Integer, String> phraseColumns, int currentColumn) {
        for (Integer columnIndex : phraseColumns.keySet()) {
            if (columnIndex > currentColumn) {
                return columnIndex;
            }
        }
        return null;
    }

    private String extractDateRange(String header) {
        Matcher matcher = HEADER_DATE_RANGE_PATTERN.matcher(header);
        if (!matcher.find()) {
            return header;
        }
        return normalizeHeaderDate(matcher.group(1)) + " - " + normalizeHeaderDate(matcher.group(2));
    }

    private String normalizeHeaderDate(String date) {
        if (date.length() == 8) {
            return date.substring(0, 6) + "20" + date.substring(6);
        }
        return date;
    }

    private String extractMonthFromDateRange(String dateRange, boolean first) {
        String[] dates = dateRange.split(" - ");
        String date = first ? dates[0] : dates[1];
        return date.substring(3);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("нет")) {
            return null;
        }

        try {
            return Integer.parseInt(value.replace(" ", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String convertDate(String date) {
        String[] parts = date.split("\\.");
        return parts[2] + "-" + parts[1] + "-" + parts[0];
    }

    private String normalizeToFirstDayOfMonth(String date) {
        YearMonth yearMonth = YearMonth.parse(date, INPUT_MONTH_FORMATTER);
        return yearMonth.atDay(1).format(OUTPUT_DATE_FORMATTER);
    }

    private String normalizeToLastDayOfMonth(String date) {
        YearMonth yearMonth = YearMonth.parse(date, INPUT_MONTH_FORMATTER);
        return yearMonth.atEndOfMonth().format(OUTPUT_DATE_FORMATTER);
    }
}
