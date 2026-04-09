package org.paring.model;

import lombok.Data;

@Data
public class ResponseToSaveToExcelDTO {
    private String phrase;
    private Integer countSum;
    private String date;
}
