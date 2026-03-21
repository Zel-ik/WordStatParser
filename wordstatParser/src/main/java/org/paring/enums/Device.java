package org.paring.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Device {
    DESKTOP("desktop"),
    PHONE("phone"),
    TABLET("tablet"),
    ALL("all");

    private final String type;
}
