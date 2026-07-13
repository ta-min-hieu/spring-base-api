package com.ringme.base.service.support;

import com.ringme.base.config.storage.StorageProperties;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.FileCategory;
import com.ringme.base.exception.BusinessLogicException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Logic dùng chung giữa {@code FileStorageServiceImpl} (upload trực tiếp, file nhỏ) và
 * {@code ChunkedUploadServiceImpl} (upload theo từng đoạn, file lớn): validate category/size/content-type,
 * sinh tên file lưu trữ + đường dẫn theo ngày, và chặn path traversal.
 */
@Component
@RequiredArgsConstructor
public class FileStorageSupport {

    private final StorageProperties storageProperties;

    public FileCategory detectCategory(String contentType) {
        if (contentType == null) {
            throw new BusinessLogicException(AppCode.CODE_400, "Missing file content type");
        }
        if (contentType.startsWith("image/")) {
            return FileCategory.IMAGE;
        }
        if (contentType.startsWith("video/")) {
            return FileCategory.VIDEO;
        }
        throw new BusinessLogicException(AppCode.CODE_400, "Unsupported file type: " + contentType);
    }

    public void validateSize(FileCategory category, long size) {
        long max = (category == FileCategory.IMAGE
                ? storageProperties.getMaxImageSize() : storageProperties.getMaxVideoSize()).toBytes();
        if (size > max) {
            throw new BusinessLogicException(AppCode.CODE_400, "File too large: " + size + " bytes (max " + max + ")");
        }
    }

    public void validateContentType(FileCategory category, String contentType) {
        List<String> allowed = category == FileCategory.IMAGE
                ? storageProperties.getAllowedImageTypes() : storageProperties.getAllowedVideoTypes();
        if (!allowed.isEmpty() && !allowed.contains(contentType)) {
            throw new BusinessLogicException(AppCode.CODE_400, "Content type not allowed: " + contentType);
        }
    }

    /** Thư mục tương đối theo ngày, vd {@code images/2026/07/13}. */
    public String relativeDirFor(FileCategory category, LocalDate date) {
        return "%s/%04d/%02d/%02d".formatted(
                category == FileCategory.IMAGE ? "images" : "videos",
                date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    public String newStoredFileName(String originalFilename) {
        return UUID.randomUUID() + sanitizedExtension(originalFilename);
    }

    public Path rootDir() {
        return Path.of(storageProperties.getRootDir());
    }

    /**
     * Ghép rootDir/relativeDir/fileName và chốt lại rằng kết quả thực sự nằm trong relativeDir
     * (phòng path traversal dù storedFileName đã được sinh an toàn ở {@link #newStoredFileName}).
     */
    public Path resolveTargetFile(Path targetDir, String fileName) {
        Path targetFile = targetDir.resolve(fileName).normalize();
        if (!targetFile.startsWith(targetDir)) {
            throw new BusinessLogicException(AppCode.CODE_400, "Invalid file name");
        }
        return targetFile;
    }

    /** Lấy đuôi file từ tên gốc, bỏ mọi phần thư mục/ký tự khác chữ-số để chặn path traversal. */
    private String sanitizedExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        String name;
        try {
            // originalFilename do client gửi lên, có thể chứa ký tự không hợp lệ với Path của hệ điều hành
            // (vd ':' trên Windows) -> Path.of ném InvalidPathException, coi như không xác định được đuôi file.
            name = Path.of(originalFilename).getFileName().toString();
        } catch (Exception e) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot + 1).replaceAll("[^a-zA-Z0-9]", "");
        if (ext.isEmpty()) {
            return "";
        }
        return "." + (ext.length() > 10 ? ext.substring(0, 10) : ext);
    }
}
