package com.ringme.base.repository;

import com.ringme.base.entity.UploadFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UploadFileRepository extends JpaRepository<UploadFile, Long> {

    /**
     * File đã tạo trước {@code threshold} nhưng chưa được product nào tham chiếu — vd chunked upload
     * complete xong nhưng người dùng bỏ dở form, không bao giờ gọi create/update product để gắn.
     * Dùng cho job dọn định kỳ, xem {@code OrphanedUploadFileCleanupTask}.
     */
    @Query("SELECT uf FROM UploadFile uf WHERE uf.createdAt < :threshold "
            + "AND uf.id NOT IN (SELECT puf.uploadFile.id FROM ProductUploadFile puf)")
    List<UploadFile> findOrphaned(@Param("threshold") LocalDateTime threshold);
}
