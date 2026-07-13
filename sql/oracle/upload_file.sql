-- Bổ sung tính năng upload file (ảnh/video) cho sản phẩm.
-- Không có Flyway/Liquibase trong repo này (xem CLAUDE.md) nên script này chỉ để tham khảo/tái tạo thủ công;
-- đã được chạy trực tiếp vào container oracle-xe (schema dev_e_commerce) qua sqlplus.

-- Thông tin file đã upload; file_path là đường dẫn TƯƠNG ĐỐI so với app.storage.root-dir
-- (dạng images|videos/yyyy/MM/dd/<uuid>.<ext>), không lưu đường dẫn tuyệt đối để có thể đổi root-dir theo môi trường.
CREATE TABLE dev_e_commerce.upload_file (
    id                  NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    original_file_name  VARCHAR2(255) NOT NULL,
    stored_file_name    VARCHAR2(255) NOT NULL,
    file_path           VARCHAR2(500) NOT NULL,
    content_type        VARCHAR2(100) NOT NULL,
    file_category       VARCHAR2(20) NOT NULL,
    file_size           NUMBER(19) NOT NULL,
    created_at          TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

-- Bảng nối product <-> upload_file: 1 product có thể có nhiều ảnh/video (gallery), sort_order để giữ thứ tự hiển thị.
CREATE TABLE dev_e_commerce.product_upload_file (
    id              NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    product_id      NUMBER NOT NULL,
    upload_file_id  NUMBER NOT NULL,
    sort_order      NUMBER(10) DEFAULT 0 NOT NULL,
    CONSTRAINT fk_puf_product FOREIGN KEY (product_id)
        REFERENCES dev_e_commerce.product (id) ON DELETE CASCADE,
    CONSTRAINT fk_puf_upload_file FOREIGN KEY (upload_file_id)
        REFERENCES dev_e_commerce.upload_file (id) ON DELETE CASCADE,
    CONSTRAINT uq_puf_product_file UNIQUE (product_id, upload_file_id)
);

CREATE INDEX idx_puf_product ON dev_e_commerce.product_upload_file (product_id);
