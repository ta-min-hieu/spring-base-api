package com.ringme.base.converter.persistence;

import com.ringme.base.enums.ProductStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Lưu {@link ProductStatus} dưới dạng chuỗi thường ("active"/"inactive") thay vì tên enum Java. */
@Converter(autoApply = true)
public class ProductStatusConverter implements AttributeConverter<ProductStatus, String> {

    @Override
    public String convertToDatabaseColumn(ProductStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ProductStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ProductStatus.initFrom(dbData);
    }
}
