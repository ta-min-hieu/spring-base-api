package com.ringme.base.infra.mysql;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity CHỈ DÙNG CHO TEST — dựng bảng tạm để kiểm chứng đường BẬT MySQL (JPA + Hibernate).
 * Nằm trong test classpath nên KHÔNG ảnh hưởng base thật; khi MySQL TẮT, Spring Data JPA
 * "backs-off" (không có EntityManagerFactory) nên repository không được tạo bean.
 */
@Entity
@Table(name = "demo_jpa_entity")
@Getter
@Setter
@NoArgsConstructor
public class DemoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    public DemoJpaEntity(String name) {
        this.name = name;
    }
}
