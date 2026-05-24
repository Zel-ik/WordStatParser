package org.paring.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DateRangeInput {
    private String dateFrom;
    private String dateTo;
}
