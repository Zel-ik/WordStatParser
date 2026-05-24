package org.paring.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkbookRowResult {
    private String universityName;
    private List<WorkbookBlockResult> blocks = new ArrayList<>();
}
