package org.paring.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UniRequest {
    private String phrase;
    private String period;
    private String fromDate;
    private String toDate;
    private List<String> devices = new ArrayList<>();
    public UniRequest() {
    }
}
