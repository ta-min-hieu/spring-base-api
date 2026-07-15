package com.ringme.base.enums.iam;

/**
 * Placeholder cho việc mở rộng Data Permission (RBAC + Data Scope) trong TƯƠNG LAI — hiện tại CHỈ
 * lưu giá trị trên {@code Role}, CHƯA có logic lọc dữ liệu theo scope ở tầng service/repository.
 * ALL: thấy toàn bộ dữ liệu. SELF: chỉ dữ liệu do chính mình tạo. DEPT: dữ liệu trong phòng ban.
 * CUSTOM: phạm vi tuỳ biến (vd danh sách phòng ban chỉ định) — cần bảng phụ khi triển khai thật.
 */
public enum DataScope {
    ALL,
    SELF,
    DEPT,
    CUSTOM,
}
