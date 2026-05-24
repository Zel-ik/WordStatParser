package org.paring.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PhraseBlockInput {
    private String phrase;
    private List<DateRangeInput> dateRanges = new ArrayList<>();
}
