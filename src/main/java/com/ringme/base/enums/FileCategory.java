package com.ringme.base.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Phân loại file đã upload — ảnh hoặc video, suy ra từ content-type lúc upload. */
public enum FileCategory {
    IMAGE("image"),
    VIDEO("video"),
    ;

    private final String VALUE;

    @JsonValue
    public String getValue() {
        return this.VALUE;
    }

    FileCategory(String value) {
        this.VALUE = value;
    }

    @JsonCreator
    public static FileCategory initFrom(String value) {
        for (FileCategory instance : FileCategory.values()) {
            if (instance.VALUE.equalsIgnoreCase(value)) {
                return instance;
            }
        }
        throw new IllegalArgumentException("Unknown FileCategory: " + value);
    }
}
