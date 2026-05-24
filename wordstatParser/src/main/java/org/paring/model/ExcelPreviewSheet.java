package org.paring.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExcelPreviewSheet {
    private String sheetName;
    private List<ExcelPreviewUniversity> universities = new ArrayList<>();
}
