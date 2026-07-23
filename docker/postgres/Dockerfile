# postgres:16 (Debian) đã bỏ các alias tzdata "backward-compat" cũ (vd Asia/Saigon -> Asia/Ho_Chi_Minh)
# khỏi /usr/share/zoneinfo — client nào tự gửi TimeZone theo tên cũ lúc kết nối (vd DBeaver/JVM trên
# Windows tiếng Việt vẫn map sang "Asia/Saigon") sẽ bị Postgres từ chối ngay từ bước bắt tay:
# "FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"". Tạo lại symlink để phục hồi tên cũ.
FROM postgres:16
RUN ln -sf /usr/share/zoneinfo/Asia/Ho_Chi_Minh /usr/share/zoneinfo/Asia/Saigon
