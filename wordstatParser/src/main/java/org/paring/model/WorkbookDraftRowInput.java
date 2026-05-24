package org.paring.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkbookDraftRowInput {
    private String universityName;
    private List<PhraseBlockInput> blocks = new ArrayList<>();
}
