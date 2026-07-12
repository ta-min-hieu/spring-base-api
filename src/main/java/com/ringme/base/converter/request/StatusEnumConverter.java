package com.ringme.base.converter.request;

import com.ringme.base.enums.Status;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class StatusEnumConverter implements Converter<String, Status> {
    // Spring Framework 7 dùng JSpecify; tham số mặc định non-null nên không cần @NonNull, chỉ đánh dấu return có thể null.
    @Override
    public @Nullable Status convert(String source) {
        try {
            return Status.initFrom(Integer.parseInt(source));
        } catch (Exception e) {
            log.error("ERROR CONVERTING REQUEST PARAM ENUM: {}", source, e);
            return null;
        }
    }
}
