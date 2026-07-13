package com.ringme.base.entity;

import com.ringme.base.enums.ProductStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ánh xạ bảng {@code dev_e_commerce.product} — khớp interface {@code Product} phía Angular
 * (features/products/product.model.ts).
 */
@Getter
@Setter
@Entity
@Table(name = "product", schema = "dev_e_commerce")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private String category;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_tag", schema = "dev_e_commerce", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(nullable = false)
    private Boolean featured;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // EAGER giống 'tags' ở trên: open-in-view=false nên list/getById (không @Transactional) sẽ
    // vỡ LazyInitializationException nếu để LAZY.
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC")
    private List<ProductUploadFile> uploadFiles = new ArrayList<>();
}
