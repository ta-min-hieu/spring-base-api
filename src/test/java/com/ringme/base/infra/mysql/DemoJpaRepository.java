package com.ringme.base.infra.mysql;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository CHỈ DÙNG CHO TEST cho {@link DemoJpaEntity}. Khi MySQL TẮT (mặc định),
 * Spring Data JPA không kích hoạt -> interface này không sinh bean, không gây lỗi context.
 */
public interface DemoJpaRepository extends JpaRepository<DemoJpaEntity, Long> {
}
