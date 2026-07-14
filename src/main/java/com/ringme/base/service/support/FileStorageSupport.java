package com.ringme.base.service.support;

import com.ringme.base.config.storage.StorageProperties;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.FileCategory;
import com.ringme.base.exception.BusinessLogicException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    private static final int MAGIC_PROBE_SIZE = 16;

    /**
     * Đối chiếu vài byte đầu của file thật trên đĩa với chữ ký (magic number) đã biết của
     * {@code contentType} — validateContentType chỉ tin Content-Type client TỰ khai báo, không đọc
     * nội dung thật, nên 1 file .exe đổi tên/khai báo "image/png" vẫn lọt qua nếu không có bước này.
     * Content-type KHÔNG có trong bảng chữ ký (vd định dạng hiếm ai đó tự thêm vào allow-list) thì bỏ
     * qua (fail-open) thay vì chặn nhầm, vì bảng này không thể đầy đủ mọi định dạng.
     */
    public void verifyMagicBytes(String contentType, Path file) {
        List<MagicSignature> signatures = MAGIC_SIGNATURES.get(contentType);
        if (signatures == null) {
            return;
        }
        byte[] header = readHeader(file);
        boolean matches = signatures.stream().allMatch(sig -> sig.matches(header));
        if (!matches) {
            throw new BusinessLogicException(AppCode.CODE_400,
                    "File content does not match declared content type: " + contentType);
        }
    }

    private byte[] readHeader(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[MAGIC_PROBE_SIZE];
            int total = 0;
            int read;
            while (total < buffer.length && (read = in.read(buffer, total, buffer.length - total)) != -1) {
                total += read;
            }
            return total == buffer.length ? buffer : Arrays.copyOf(buffer, total);
        } catch (IOException e) {
            throw new BusinessLogicException(AppCode.CODE_500, "Cannot read file for validation");
        }
    }

    /** 1 đoạn byte cố định phải khớp tại {@code offset} — 1 content-type có thể cần NHIỀU đoạn (vd WEBP). */
    private record MagicSignature(int offset, byte[] magic) {
        boolean matches(byte[] header) {
            if (header.length < offset + magic.length) {
                return false;
            }
            for (int i = 0; i < magic.length; i++) {
                if (header[offset + i] != magic[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    private static MagicSignature ascii(int offset, String text) {
        return new MagicSignature(offset, text.getBytes(StandardCharsets.US_ASCII));
    }

    private static MagicSignature bytes(int offset, int... unsignedBytes) {
        byte[] magic = new byte[unsignedBytes.length];
        for (int i = 0; i < unsignedBytes.length; i++) {
            magic[i] = (byte) unsignedBytes[i];
        }
        return new MagicSignature(offset, magic);
    }

    // Chỉ phủ đúng các content-type mặc định trong app.storage.allowed-*-types (STORAGE_ALLOWED_*_TYPES).
    private static final Map<String, List<MagicSignature>> MAGIC_SIGNATURES = Map.of(
            "image/jpeg", List.of(bytes(0, 0xFF, 0xD8, 0xFF)),
            "image/png", List.of(bytes(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
            "image/gif", List.of(ascii(0, "GIF8")),
            "image/webp", List.of(ascii(0, "RIFF"), ascii(8, "WEBP")),
            "video/mp4", List.of(ascii(4, "ftyp")),
            "video/quicktime", List.of(ascii(4, "ftyp")),
            "video/x-msvideo", List.of(ascii(0, "RIFF"), ascii(8, "AVI ")),
            "video/webm", List.of(bytes(0, 0x1A, 0x45, 0xDF, 0xA3))
    );

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
