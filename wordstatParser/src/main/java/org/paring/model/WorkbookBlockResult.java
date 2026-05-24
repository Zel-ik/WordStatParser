package org.paring.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkbookBlockResult {
    private String phrase;
    private List<ResponseToSaveToExcelDTO> results = new ArrayList<>();
}
