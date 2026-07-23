package com.ringme.base.converter.request;

import com.ringme.base.enums.ProductStatus;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

// Cùng mẫu với StatusEnumConverter — nhưng ProductStatus.initFrom() so khớp case-insensitive theo
// value ("active"/"inactive", xem ProductStatus), không phải theo số như Status.
@Component
@Log4j2
public class ProductStatusEnumConverter implements Converter<String, ProductStatus> {
    @Override
    public @Nullable ProductStatus convert(String source) {
        try {
            return ProductStatus.initFrom(source);
        } catch (Exception e) {
            log.error("ERROR CONVERTING REQUEST PARAM ENUM: {}", source, e);
            return null;
        }
    }
}
