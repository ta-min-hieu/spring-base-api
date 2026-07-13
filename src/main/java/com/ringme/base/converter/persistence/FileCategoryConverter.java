package com.ringme.base.converter.persistence;

import com.ringme.base.enums.FileCategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Lưu {@link FileCategory} dưới dạng chuỗi thường ("image"/"video") thay vì tên enum Java. */
@Converter(autoApply = true)
public class FileCategoryConverter implements AttributeConverter<FileCategory, String> {

    @Override
    public String convertToDatabaseColumn(FileCategory attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public FileCategory convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FileCategory.initFrom(dbData);
    }
}
