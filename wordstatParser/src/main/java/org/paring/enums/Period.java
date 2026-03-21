package org.paring.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Period {
    MONTHLY("monthly"),
    WEEKLY("weekly"),
    DAILY("daily");

    private final String name;
}
