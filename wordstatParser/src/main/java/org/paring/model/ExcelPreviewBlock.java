package org.paring.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExcelPreviewBlock {
    private String sheetName;
    private int headerRowIndex;
    private int rowIndex;
    private int phraseColumnIndex;
    private String phrase;
    private List<ExcelPreviewResult> results = new ArrayList<>();
}
