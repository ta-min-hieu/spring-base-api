package com.ringme.base.repository;

import com.ringme.base.entity.Product;
import com.ringme.base.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 3 filter đều tuỳ chọn (NULL = bỏ qua điều kiện đó) và kết hợp được với nhau (AND) — gộp vào 1
    // query thay vì nhiều derived-query method riêng lẻ theo từng tổ hợp tham số.
    @Query("""
            SELECT p FROM Product p
            WHERE (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:category IS NULL OR p.category = :category)
              AND (:status IS NULL OR p.status = :status)
            """)
    Page<Product> search(
            @Param("name") String name,
            @Param("category") String category,
            @Param("status") ProductStatus status,
            Pageable pageable);
}
