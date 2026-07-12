package com.ringme.base.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh"),
    ;

    private final String VALUE;

    @JsonValue
    public String getValue() {
        return this.VALUE;
    }

    public boolean matches(String value) {
        try {
            return this.VALUE.equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    TokenType(String value) {
        this.VALUE = value;
    }

    @JsonCreator
    public static TokenType initFrom(String value) {
        try {
            for (TokenType instance : TokenType.values()) {
                if (instance.VALUE.equals(value)) {
                    return instance;
                }
            }
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}