package com.ringme.base.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {
    INACTIVE(0),
    ACTIVE(1),
    ;

    private final Integer VALUE;

    @JsonValue
    public Integer getValue() {
        return this.VALUE;
    }

    public boolean equals(Integer value) {
        try {
            return this.VALUE.equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    Status(Integer value) {
        this.VALUE = value;
    }

    @JsonCreator
    public static Status initFrom(Integer value) {
        try {
            for (Status instance : Status.values()) {
                if (instance.VALUE.equals(value)) {
                    return instance;
                }
            }
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
