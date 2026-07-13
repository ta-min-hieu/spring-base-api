package com.ringme.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Ánh xạ bảng {@code dev_e_commerce.product_upload_file} — bảng nối product &lt;-&gt; upload_file
 * (1 product có nhiều ảnh/video, {@code sortOrder} giữ thứ tự hiển thị).
 */
@Getter
@Setter
@Entity
@Table(name = "product_upload_file", schema = "dev_e_commerce",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "upload_file_id"}))
public class ProductUploadFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "upload_file_id", nullable = false)
    private UploadFile uploadFile;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
