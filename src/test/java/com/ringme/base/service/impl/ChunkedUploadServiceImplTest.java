package com.ringme.base.service.impl;

import com.ringme.base.config.storage.StorageProperties;
import com.ringme.base.dto.app.request.InitChunkedUploadRequest;
import com.ringme.base.dto.app.response.ChunkUploadProgressResponse;
import com.ringme.base.dto.app.response.InitChunkedUploadResponse;
import com.ringme.base.entity.UploadFile;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.UploadFileRepository;
import com.ringme.base.service.support.FileStorageSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần cho ChunkedUploadServiceImpl: dùng {@link TempDir} làm root-dir thật, chỉ mock
 * UploadFileRepository. Gọi thẳng {@code init()} (@PostConstruct) sau khi new vì không chạy trong
 * Spring context.
 */
class ChunkedUploadServiceImplTest {

    @TempDir
    Path rootDir;

    private StorageProperties storageProperties;
    private UploadFileRepository uploadFileRepository;
    private ChunkedUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        storageProperties.setRootDir(rootDir.toString());
        storageProperties.setMaxChunkSessions(100);
        storageProperties.setChunkSessionTtl(Duration.ofMinutes(30));

        uploadFileRepository = mock(UploadFileRepository.class);
        when(uploadFileRepository.save(any(UploadFile.class))).thenAnswer(inv -> inv.getArgument(0));

        FileStorageSupport support = new FileStorageSupport(storageProperties);
        service = new ChunkedUploadServiceImpl(storageProperties, uploadFileRepository, support);
        service.init();
    }

    // "video/x-test" (không phải "video/mp4"): vẫn qua được detectCategory (prefix "video/") nhưng
    // KHÔNG có trong bảng chữ ký magic-byte -> fail-open, các test chunk/assembly dưới đây không cần
    // construct payload MP4 thật. Test riêng cho magic-byte dùng "video/mp4" + payload cố tình sai.
    private InitChunkedUploadRequest request(long fileSize, long chunkSize) {
        return InitChunkedUploadRequest.builder()
                .originalFileName("video.mp4")
                .contentType("video/x-test")
                .fileSize(fileSize)
                .chunkSize(chunkSize)
                .build();
    }

    /** Hạ min-chunk-size (mặc định 256KB) để test được logic ghép byte với chunk vài byte cho gọn. */
    private void allowTinyChunks() {
        storageProperties.setMinChunkSize(DataSize.ofBytes(1));
    }

    // ===== init =====

    @Test
    void init_rejectsUnsupportedContentType() {
        InitChunkedUploadRequest req = InitChunkedUploadRequest.builder()
                .originalFileName("x.exe").contentType("application/x-msdownload")
                .fileSize(1000).chunkSize(300_000).build();
        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.init(req));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void init_rejectsOversizedFile() {
        InitChunkedUploadRequest req = request(9_999_999_999L, 300_000);
        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.init(req));
        assertEquals(AppCode.CODE_400, ex.getCode());
        assertTrue(ex.getMessage().contains("too large"));
    }

    @Test
    void init_rejectsChunkSizeBelowMin() {
        InitChunkedUploadRequest req = request(1000, 10);
        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.init(req));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void init_rejectsChunkSizeAboveMax() {
        InitChunkedUploadRequest req = request(1000, 999_999_999);
        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.init(req));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void init_computesTotalChunksAsCeilDivision() {
        // 2_500_000 / 1_000_000 = 2.5 -> phải làm tròn lên 3, không được cắt xuống 2.
        InitChunkedUploadResponse resp = service.init(request(2_500_000, 1_000_000));
        assertEquals(3, resp.getTotalChunks());
        assertEquals(1_000_000, resp.getChunkSize());
    }

    // ===== writeChunk =====

    @Test
    void writeChunk_rejectsUnknownUploadId() {
        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.writeChunk("does-not-exist", 0, emptyStream()));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }

    @Test
    void writeChunk_rejectsOutOfRangeChunkIndex() {
        allowTinyChunks();
        InitChunkedUploadResponse init = service.init(request(1000, 500)); // totalChunks = 2
        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.writeChunk(init.getUploadId(), 2, emptyStream()));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void writeChunk_outOfOrder_assemblesBytesCorrectly() {
        allowTinyChunks();
        byte[] full = "AAAAABBBBBCCCCC".getBytes(StandardCharsets.UTF_8); // 15 bytes, 3 chunks of 5
        InitChunkedUploadResponse init = service.init(request(full.length, 5));
        assertEquals(3, init.getTotalChunks());
        String uploadId = init.getUploadId();

        // Gửi ngược thứ tự: 2, 0, 1 — kiểm tra positioned-write không phụ thuộc thứ tự đến.
        ChunkUploadProgressResponse p1 = service.writeChunk(uploadId, 2, streamOf(full, 10, 5));
        assertEquals(1, p1.getReceivedChunks());
        assertFalse(p1.isCompleted());

        ChunkUploadProgressResponse p2 = service.writeChunk(uploadId, 0, streamOf(full, 0, 5));
        assertEquals(2, p2.getReceivedChunks());
        assertFalse(p2.isCompleted());

        ChunkUploadProgressResponse p3 = service.writeChunk(uploadId, 1, streamOf(full, 5, 5));
        assertEquals(3, p3.getReceivedChunks());
        assertTrue(p3.isCompleted());

        UploadFile result = service.complete(uploadId);
        assertEquals((long) full.length, result.getFileSize());

        Path assembled = rootDir.resolve(result.getFilePath());
        assertArrayEquals(full, readAll(assembled));
    }

    @Test
    void writeChunk_rejectsChunkExceedingExpectedSize() {
        allowTinyChunks();
        // 1 chunk duy nhất, expected = 5 byte, nhưng client gửi 10 byte thô (body PUT không phải
        // multipart nên spring.servlet.multipart.max-file-size không chặn được request này).
        InitChunkedUploadResponse init = service.init(request(5, 5));
        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.writeChunk(init.getUploadId(), 0, streamOf("0123456789".getBytes(), 0, 10)));
        assertEquals(AppCode.CODE_400, ex.getCode());
        assertTrue(ex.getMessage().contains("exceeds expected size"));
    }

    // ===== complete =====

    @Test
    void complete_rejectsWhenAssembledContentDoesNotMatchDeclaredMagicBytes() {
        allowTinyChunks();
        // Khai contentType "video/mp4" (CÓ trong bảng chữ ký) nhưng payload không phải MP4 thật.
        InitChunkedUploadRequest req = InitChunkedUploadRequest.builder()
                .originalFileName("fake.mp4").contentType("video/mp4").fileSize(5).chunkSize(5).build();
        InitChunkedUploadResponse init = service.init(req);
        service.writeChunk(init.getUploadId(), 0, streamOf("hello".getBytes(), 0, 5));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.complete(init.getUploadId()));
        assertEquals(AppCode.CODE_400, ex.getCode());
        assertTrue(ex.getMessage().contains("does not match"));

        // File giả (đã move sang vị trí cuối) phải bị dọn, và phiên bị hủy (thao tác tiếp theo -> 404).
        assertEquals(0, countRegularFilesUnder(rootDir.resolve("videos")));
        assertThrows(BusinessLogicException.class, () -> service.complete(init.getUploadId()));
    }

    @Test
    void complete_rejectsWhenChunksIncomplete() {
        allowTinyChunks();
        InitChunkedUploadResponse init = service.init(request(10, 5)); // 2 chunks
        service.writeChunk(init.getUploadId(), 0, streamOf("hello".getBytes(), 0, 5));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.complete(init.getUploadId()));
        assertEquals(AppCode.CODE_400, ex.getCode());
        assertTrue(ex.getMessage().contains("incomplete") || ex.getMessage().contains("Incomplete"));
    }

    @Test
    void complete_rejectsWhenAssembledSizeDoesNotMatchDeclaredSize() {
        allowTinyChunks();
        // Khai báo fileSize=10 nhưng chỉ thực sự ghi 5 byte cho chunk duy nhất (client khai sai).
        InitChunkedUploadResponse init = service.init(request(10, 20));
        assertEquals(1, init.getTotalChunks());
        service.writeChunk(init.getUploadId(), 0, streamOf("short".getBytes(), 0, 5));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.complete(init.getUploadId()));
        assertEquals(AppCode.CODE_400, ex.getCode());
        assertTrue(ex.getMessage().contains("mismatch"));
    }

    @Test
    void complete_movesAssembledFileAndSavesUploadFile() {
        allowTinyChunks();
        byte[] content = "full-content".getBytes(StandardCharsets.UTF_8);
        InitChunkedUploadResponse init = service.init(request(content.length, content.length));
        service.writeChunk(init.getUploadId(), 0, streamOf(content, 0, content.length));

        UploadFile saved = service.complete(init.getUploadId());

        assertEquals("video.mp4", saved.getOriginalFileName());
        assertEquals("video/x-test", saved.getContentType());
        assertEquals((long) content.length, saved.getFileSize());
        Path assembled = rootDir.resolve(saved.getFilePath());
        assertTrue(Files.exists(assembled));
        assertArrayEquals(content, readAll(assembled));

        // Thư mục tạm không còn giữ file part sau khi move.
        Path tempDir = rootDir.resolve(".uploads-tmp");
        assertEquals(0, countFiles(tempDir));
    }

    @Test
    void complete_invalidatesSession_soFurtherOperationsAre404() {
        allowTinyChunks();
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);
        InitChunkedUploadResponse init = service.init(request(1, 1));
        service.writeChunk(init.getUploadId(), 0, streamOf(content, 0, 1));
        service.complete(init.getUploadId());

        assertThrows(BusinessLogicException.class, () -> service.complete(init.getUploadId()));
        assertThrows(BusinessLogicException.class,
                () -> service.writeChunk(init.getUploadId(), 0, emptyStream()));
    }

    // ===== abort =====

    @Test
    void abort_rejectsUnknownUploadId() {
        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.abort("does-not-exist"));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }

    @Test
    void abort_deletesTempFileAndInvalidatesSession() {
        allowTinyChunks();
        InitChunkedUploadResponse init = service.init(request(10, 5));
        service.writeChunk(init.getUploadId(), 0, streamOf("hello".getBytes(), 0, 5));

        Path tempDir = rootDir.resolve(".uploads-tmp");
        assertEquals(1, countFiles(tempDir));

        service.abort(init.getUploadId());

        // Caffeine removal listener chạy async -> chờ ngắn để file tạm được dọn.
        awaitTempDirEmpty(tempDir);
        assertThrows(BusinessLogicException.class, () -> service.complete(init.getUploadId()));
    }

    // ===== helpers =====

    private static InputStream emptyStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private static InputStream streamOf(byte[] full, int offset, int length) {
        return new ByteArrayInputStream(Arrays.copyOfRange(full, offset, offset + length));
    }

    private static byte[] readAll(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static long countRegularFilesUnder(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static long countFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var stream = Files.list(dir)) {
            return stream.count();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void awaitTempDirEmpty(Path tempDir) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && countFiles(tempDir) > 0) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        assertEquals(0, countFiles(tempDir));
    }
}
