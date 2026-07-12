package com.ringme.base.service.common;

public interface MultiLangManager {
    String EXAMPLE = "EXAMPLE";

    String getMessage(String key, String language);

    String getMessage(String key, String language, Object... args);

    /** Như getMessage nhưng khi không tìm thấy key thì trả về defaultMessage thay vì chính key. */
    String getMessageOrDefault(String key, String language, String defaultMessage);
}
