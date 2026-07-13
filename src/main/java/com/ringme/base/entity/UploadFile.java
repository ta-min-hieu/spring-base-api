package com.ringme.base.entity;

import com.ringme.base.enums.FileCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ánh xạ bảng {@code dev_e_commerce.upload_file} — thông tin 1 file (ảnh/video) đã upload.
 * {@code filePath} là đường dẫn TƯƠNG ĐỐI so với {@code app.storage.root-dir}
 * (dạng {@code images|videos/yyyy/MM/dd/<uuid>.<ext>}), không lưu đường dẫn tuyệt đối.
 */
@Getter
@Setter
@Entity
@Table(name = "upload_file", schema = "dev_e_commerce")
public class UploadFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false)
    private String storedFileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_category", nullable = false, length = 20)
    private FileCategory fileCategory;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
