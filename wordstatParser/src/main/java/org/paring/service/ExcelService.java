package org.paring.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.paring.model.ResponseToSaveToExcelDTO;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

@Slf4j
public class ExcelService {
    public void createBook(ResponseToSaveToExcelDTO response) {
        try (Workbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Мой лист");

            // (отсчет идет с 0)
            Row row2 = sheet.createRow(1);
            Row row1 = sheet.createRow(0);

            row1.createCell(7).setCellValue(response.getDate());
            row2.createCell(7).setCellValue(response.getCountSum());
            row2.createCell(6).setCellValue(response.getPhrase());

            try (FileOutputStream fileOut = new FileOutputStream("result.xls")) {
                workbook.write(fileOut);
                System.out.println("Файл успешно создан!");
            } catch (IOException e) {
                log.error("Ошибка при попытке сохранить excel файл\n" + e.getMessage());
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            log.error("Ошибка при попытке создать excel файл\n" +e.getMessage());
        }
    }
}
