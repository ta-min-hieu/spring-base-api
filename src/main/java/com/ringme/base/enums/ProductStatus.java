package com.ringme.base.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Khớp type {@code ProductStatus = 'active' | 'inactive'} phía Angular (product.model.ts). */
public enum ProductStatus {
    ACTIVE("active"),
    INACTIVE("inactive"),
    ;

    private final String VALUE;

    @JsonValue
    public String getValue() {
        return this.VALUE;
    }

    ProductStatus(String value) {
        this.VALUE = value;
    }

    @JsonCreator
    public static ProductStatus initFrom(String value) {
        for (ProductStatus instance : ProductStatus.values()) {
            if (instance.VALUE.equalsIgnoreCase(value)) {
                return instance;
            }
        }
        throw new IllegalArgumentException("Unknown ProductStatus: " + value);
    }
}
