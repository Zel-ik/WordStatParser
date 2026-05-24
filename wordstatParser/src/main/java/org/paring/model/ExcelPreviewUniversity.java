package org.paring.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExcelPreviewUniversity {
    private int rowIndex;
    private int fullNameColumnIndex;
    private String universityName;
    private List<ExcelPreviewBlock> blocks = new ArrayList<>();
}
