package com.ringme.base.service.impl;

import com.ringme.base.config.storage.StorageProperties;
import com.ringme.base.entity.UploadFile;
import com.ringme.base.repository.UploadFileRepository;
import com.ringme.base.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dọn định kỳ các {@code upload_file} chưa được product nào tham chiếu — chủ yếu là hệ quả của
 * chunked upload: {@code complete()} đã tạo bản ghi + ghi file xuống đĩa, nhưng nếu người dùng bỏ dở
 * form (không bao giờ gọi tạo/sửa product với {@code fileIds} chứa file đó) thì file này mồ côi
 * vĩnh viễn nếu không có job này — file upload trực tiếp kèm product (part {@code files}) không rơi
 * vào trường hợp này vì luôn được gắn link trong cùng transaction lúc tạo.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class OrphanedUploadFileCleanupTask {

    private final UploadFileRepository uploadFileRepository;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;

    @Scheduled(fixedDelayString = "#{@storageProperties.orphanCleanupInterval.toMillis()}")
    public void cleanupOrphanedFiles() {
        LocalDateTime threshold = LocalDateTime.now().minus(storageProperties.getOrphanFileGracePeriod());
        List<UploadFile> orphaned = uploadFileRepository.findOrphaned(threshold);
        if (orphaned.isEmpty()) {
            return;
        }
        log.info("Dọn {} file mồ côi (tạo trước {}, chưa gắn product nào)", orphaned.size(), threshold);
        // Mỗi delete() tự quản lý transaction riêng (FileStorageServiceImpl#delete là @Transactional)
        // -> 1 file lỗi không kéo rollback các file khác trong cùng lượt chạy.
        for (UploadFile uploadFile : orphaned) {
            try {
                fileStorageService.delete(uploadFile);
            } catch (Exception e) {
                log.warn("Không dọn được file mồ côi id={}: {}", uploadFile.getId(), e.getMessage());
            }
        }
    }
}
