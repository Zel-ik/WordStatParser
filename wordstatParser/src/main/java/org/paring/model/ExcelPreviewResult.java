package org.paring.model;

import lombok.Data;

@Data
public class ExcelPreviewResult {
    private String date;
    private String monthFrom;
    private String monthTo;
    private Integer countSum;
    private int columnIndex;
}
