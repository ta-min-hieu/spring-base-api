package com.ringme.base.service.impl;

import com.ringme.base.config.storage.StorageProperties;
import com.ringme.base.entity.UploadFile;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.FileCategory;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.UploadFileRepository;
import com.ringme.base.service.support.FileStorageSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần cho FileStorageServiceImpl: dùng {@link TempDir} làm root-dir thật (ghi/đọc file
 * thật trên đĩa) thay vì mock filesystem, chỉ mock UploadFileRepository (biên persistence).
 */
class FileStorageServiceImplTest {

    @TempDir
    Path rootDir;

    private UploadFileRepository uploadFileRepository;
    private StorageProperties storageProperties;
    private FileStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        storageProperties.setRootDir(rootDir.toString());
        uploadFileRepository = mock(UploadFileRepository.class);
        when(uploadFileRepository.save(any(UploadFile.class))).thenAnswer(inv -> inv.getArgument(0));

        FileStorageSupport support = new FileStorageSupport(storageProperties);
        service = new FileStorageServiceImpl(uploadFileRepository, support);
    }

    private String todayRelativeDir(String category) {
        LocalDate today = LocalDate.now();
        return category + "/" + today.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
    }

    /** Chữ ký PNG thật (89 50 4E 47 0D 0A 1A 0A) + payload bất kỳ — cần cho check magic-byte mới. */
    private static byte[] pngBytes(String payload) {
        byte[] magic = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] rest = payload.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[magic.length + rest.length];
        System.arraycopy(magic, 0, result, 0, magic.length);
        System.arraycopy(rest, 0, result, magic.length, rest.length);
        return result;
    }

    /** MP4 cần chuỗi "ftyp" ở offset 4 (4 byte đầu là box-size, giá trị thật không quan trọng ở đây). */
    private static byte[] mp4Bytes() {
        return new byte[]{0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    }

    // ===== store =====

    @Test
    void store_rejectsNullFile() {
        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.store(null));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void store_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("files", "a.png", "image/png", new byte[0]);
        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.store(file));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void store_rejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("files", "a.pdf", "application/pdf", "data".getBytes());
        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.store(file));
        assertEquals(AppCode.CODE_400, ex.getCode());
        assertTrue(ex.getMessage().contains("Unsupported file type"));
    }

    @Test
    void store_rejectsOversizedImage() {
        storageProperties.setMaxImageSize(DataSize.ofBytes(4));
        MockMultipartFile file = new MockMultipartFile("files", "a.png", "image/png", "0123456789".getBytes());
        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.store(file));
        assertEquals(AppCode.CODE_400, ex.getCode());
        assertTrue(ex.getMessage().contains("too large"));
    }

    @Test
    void store_rejectsContentTypeNotInAllowlist() {
        storageProperties.setAllowedImageTypes(List.of("image/png"));
        MockMultipartFile file = new MockMultipartFile("files", "a.jpg", "image/jpeg", "data".getBytes());
        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.store(file));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void store_writesRealFileUnderDatedPath_andSavesMetadata() throws IOException {
        byte[] content = pngBytes("hello-image-bytes");
        MockMultipartFile file = new MockMultipartFile("files", "photo.PNG", "image/png", content);

        UploadFile saved = service.store(file);

        assertEquals("photo.PNG", saved.getOriginalFileName());
        assertEquals("image/png", saved.getContentType());
        assertEquals(FileCategory.IMAGE, saved.getFileCategory());
        assertEquals((long) content.length, saved.getFileSize());
        assertTrue(saved.getFilePath().startsWith(todayRelativeDir("images") + "/"));
        assertTrue(saved.getStoredFileName().endsWith(".PNG") || saved.getStoredFileName().endsWith(".png"));

        Path onDisk = rootDir.resolve(saved.getFilePath());
        assertTrue(Files.exists(onDisk), "physical file must exist on disk");
        assertArrayEquals(content, Files.readAllBytes(onDisk));

        verify(uploadFileRepository, times(1)).save(any(UploadFile.class));
    }

    @Test
    void store_categorizesVideoIntoVideosSubdir() {
        MockMultipartFile file = new MockMultipartFile("files", "clip.mp4", "video/mp4", mp4Bytes());
        UploadFile saved = service.store(file);
        assertEquals(FileCategory.VIDEO, saved.getFileCategory());
        assertTrue(saved.getFilePath().startsWith(todayRelativeDir("videos") + "/"));
    }

    @Test
    void store_sanitizesMaliciousOriginalFilename_staysInsideTargetDir() throws IOException {
        // originalFilename cố tình chứa path traversal; extension phải được lọc sạch, file phải nằm
        // đúng trong thư mục ngày dự kiến, không thoát ra ngoài root-dir.
        MockMultipartFile file = new MockMultipartFile(
                "files", "../../../etc/passwd.png/../evil.sh", "image/png", pngBytes("x"));

        UploadFile saved = service.store(file);

        Path onDisk = rootDir.resolve(saved.getFilePath()).normalize();
        assertTrue(onDisk.startsWith(rootDir), "file must stay inside root-dir");
        assertTrue(onDisk.startsWith(rootDir.resolve(todayRelativeDir("images"))));
        assertFalse(saved.getStoredFileName().contains("/"));
        assertFalse(saved.getStoredFileName().contains(".."));
    }

    @Test
    void store_rejectsContentNotMatchingDeclaredMagicBytes() {
        // Content-Type khai "image/png" nhưng byte thật không phải PNG (vd .exe đổi tên) -> phải chặn,
        // không chỉ tin header client tự khai.
        MockMultipartFile file = new MockMultipartFile(
                "files", "fake.png", "image/png", "MZ-this-is-not-a-real-png".getBytes(StandardCharsets.UTF_8));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.store(file));
        assertEquals(AppCode.CODE_400, ex.getCode());
        assertTrue(ex.getMessage().contains("does not match"));

        // File giả phải bị dọn khỏi đĩa, không để lại rác.
        Path expectedDir = rootDir.resolve(todayRelativeDir("images"));
        assertTrue(!Files.exists(expectedDir) || isEmpty(expectedDir));
    }

    @Test
    void store_skipsMagicByteCheck_forContentTypeWithoutKnownSignature() {
        // Content-type hợp lệ theo allow-list nhưng không có trong bảng chữ ký đã biết -> fail-open,
        // không chặn nhầm định dạng hợp lệ mà app không có signature để đối chiếu.
        storageProperties.setAllowedImageTypes(List.of("image/x-custom"));
        MockMultipartFile file = new MockMultipartFile(
                "files", "a.custom", "image/x-custom", "anything-goes".getBytes(StandardCharsets.UTF_8));

        UploadFile saved = service.store(file);
        assertEquals("image/x-custom", saved.getContentType());
    }

    private static boolean isEmpty(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        } catch (IOException e) {
            return true;
        }
    }

    // ===== loadAsResource =====

    @Test
    void loadAsResource_throwsNotFound_whenPhysicalFileMissing() {
        UploadFile uploadFile = new UploadFile();
        uploadFile.setId(1L);
        uploadFile.setFilePath("images/2026/01/01/missing.png");

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.loadAsResource(uploadFile));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }

    @Test
    void loadAsResource_returnsReadableResource_whenFilePresent() throws IOException {
        Path dir = rootDir.resolve("images/2026/01/01");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("present.png"), "content");

        UploadFile uploadFile = new UploadFile();
        uploadFile.setId(2L);
        uploadFile.setFilePath("images/2026/01/01/present.png");

        Resource resource = service.loadAsResource(uploadFile);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }

    // ===== delete =====

    @Test
    void delete_removesPhysicalFileAndRepositoryRow() throws IOException {
        Path dir = rootDir.resolve("images/2026/01/01");
        Files.createDirectories(dir);
        Path physical = dir.resolve("to-delete.png");
        Files.writeString(physical, "bye");

        UploadFile uploadFile = new UploadFile();
        uploadFile.setId(3L);
        uploadFile.setFilePath("images/2026/01/01/to-delete.png");

        service.delete(uploadFile);

        assertFalse(Files.exists(physical));
        verify(uploadFileRepository, times(1)).delete(uploadFile);
    }

    @Test
    void delete_toleratesAlreadyMissingPhysicalFile() {
        UploadFile uploadFile = new UploadFile();
        uploadFile.setId(4L);
        uploadFile.setFilePath("images/2026/01/01/never-existed.png");

        service.delete(uploadFile);

        verify(uploadFileRepository, times(1)).delete(uploadFile);
    }

    // ===== findById =====

    @Test
    void findById_throwsNotFound_whenMissing() {
        when(uploadFileRepository.findById(5L)).thenReturn(Optional.empty());
        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.findById(5L));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }

    @Test
    void findById_returnsEntity_whenPresent() {
        UploadFile uploadFile = new UploadFile();
        uploadFile.setId(6L);
        when(uploadFileRepository.findById(6L)).thenReturn(Optional.of(uploadFile));

        assertEquals(uploadFile, service.findById(6L));
    }
}
