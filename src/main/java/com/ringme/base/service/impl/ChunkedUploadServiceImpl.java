package com.ringme.base.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.ringme.base.config.storage.StorageProperties;
import com.ringme.base.dto.app.request.InitChunkedUploadRequest;
import com.ringme.base.dto.app.response.ChunkUploadProgressResponse;
import com.ringme.base.dto.app.response.InitChunkedUploadResponse;
import com.ringme.base.entity.UploadFile;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.FileCategory;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.UploadFileRepository;
import com.ringme.base.service.ChunkedUploadService;
import com.ringme.base.service.support.FileStorageSupport;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Xem javadoc {@link ChunkedUploadService}. Mỗi phiên upload dở dang chỉ giữ vài chục byte metadata
 * trong bộ nhớ (Caffeine, có TTL + giới hạn số phiên) — dữ liệu file thật được ghi thẳng xuống đĩa
 * theo từng chunk (FileChannel positioned write), KHÔNG bao giờ giữ nguyên cả file trong RAM.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class ChunkedUploadServiceImpl implements ChunkedUploadService {

    private static final int COPY_BUFFER_SIZE = 8 * 1024;
    private static final String TEMP_SUBDIR = ".uploads-tmp";

    private final StorageProperties storageProperties;
    private final UploadFileRepository uploadFileRepository;
    private final FileStorageSupport support;

    private Cache<String, ChunkUploadSession> sessions;

    @PostConstruct
    void init() {
        sessions = Caffeine.newBuilder()
                .maximumSize(storageProperties.getMaxChunkSessions())
                // expireAfterAccess (không phải AfterWrite): mỗi chunk ghi thành công tính là 1 access,
                // nên 1 upload lớn/chậm vẫn sống miễn còn chunk đến đều — chỉ phiên THỰC SỰ bị bỏ dở mới hết hạn.
                .expireAfterAccess(storageProperties.getChunkSessionTtl())
                .<String, ChunkUploadSession>removalListener(
                        (String uploadId, ChunkUploadSession session, RemovalCause cause) -> {
                            if (session != null) {
                                deleteQuietly(session.tempFilePath());
                            }
                        })
                .build();
    }

    @Override
    public InitChunkedUploadResponse init(InitChunkedUploadRequest request) {
        FileCategory category = support.detectCategory(request.getContentType());
        support.validateSize(category, request.getFileSize());
        support.validateContentType(category, request.getContentType());

        long minChunk = storageProperties.getMinChunkSize().toBytes();
        long maxChunk = storageProperties.getMaxChunkSize().toBytes();
        if (request.getChunkSize() < minChunk || request.getChunkSize() > maxChunk) {
            throw new BusinessLogicException(AppCode.CODE_400,
                    "chunkSize must be between " + minChunk + " and " + maxChunk + " bytes");
        }

        int totalChunks = (int) Math.ceil((double) request.getFileSize() / request.getChunkSize());
        String uploadId = UUID.randomUUID().toString();
        String relativeDir = support.relativeDirFor(category, LocalDate.now());
        String storedFileName = support.newStoredFileName(request.getOriginalFileName());

        Path tempDir = support.rootDir().resolve(TEMP_SUBDIR);
        Path tempFilePath = tempDir.resolve(uploadId + ".part");
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            log.error("Cannot prepare temp upload dir: {}", e.getMessage(), e);
            throw new BusinessLogicException(AppCode.CODE_500, "Cannot init upload");
        }

        ChunkUploadSession session = new ChunkUploadSession(
                uploadId, request.getOriginalFileName(), request.getContentType(), category,
                request.getFileSize(), request.getChunkSize(), totalChunks,
                tempFilePath, relativeDir, storedFileName, ConcurrentHashMap.newKeySet());
        sessions.put(uploadId, session);

        return InitChunkedUploadResponse.builder()
                .uploadId(uploadId)
                .chunkSize(request.getChunkSize())
                .totalChunks(totalChunks)
                .build();
    }

    @Override
    public ChunkUploadProgressResponse writeChunk(String uploadId, int chunkIndex, InputStream chunkData) {
        ChunkUploadSession session = findSession(uploadId);
        if (chunkIndex < 0 || chunkIndex >= session.totalChunks()) {
            throw new BusinessLogicException(AppCode.CODE_400,
                    "chunkIndex out of range [0, " + session.totalChunks() + ")");
        }

        long offset = (long) chunkIndex * session.chunkSize();
        // Chặn trên: body của PUT là octet-stream thô (không phải multipart) nên
        // spring.servlet.multipart.max-file-size KHÔNG áp dụng cho request này — nếu không tự chặn ở
        // đây, 1 client gửi body vài GB vẫn bị ghi hết xuống đĩa trước khi complete() phát hiện sai lệch.
        long expectedLength = Math.min(session.chunkSize(), session.fileSize() - offset);
        try (FileChannel channel = FileChannel.open(session.tempFilePath(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            long position = offset;
            long written = 0;
            int read;
            while ((read = chunkData.read(buffer)) != -1) {
                written += read;
                if (written > expectedLength) {
                    throw new BusinessLogicException(AppCode.CODE_400,
                            "Chunk " + chunkIndex + " exceeds expected size (" + expectedLength + " bytes)");
                }
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, read);
                while (byteBuffer.hasRemaining()) {
                    position += channel.write(byteBuffer, position);
                }
            }
        } catch (IOException e) {
            log.error("Write chunk failed | uploadId: {} | chunkIndex: {} | {}", uploadId, chunkIndex, e.getMessage(), e);
            throw new BusinessLogicException(AppCode.CODE_500, "Cannot write chunk");
        }

        session.receivedChunks().add(chunkIndex);
        // getIfPresent tính là 1 access -> refresh expireAfterAccess, giữ phiên sống khi còn chunk đến.
        sessions.getIfPresent(uploadId);

        int received = session.receivedChunks().size();
        return ChunkUploadProgressResponse.builder()
                .uploadId(uploadId)
                .receivedChunks(received)
                .totalChunks(session.totalChunks())
                .completed(received == session.totalChunks())
                .build();
    }

    @Override
    @Transactional
    public UploadFile complete(String uploadId) {
        ChunkUploadSession session = findSession(uploadId);
        if (session.receivedChunks().size() != session.totalChunks()) {
            throw new BusinessLogicException(AppCode.CODE_400,
                    "Upload incomplete: " + session.receivedChunks().size() + "/" + session.totalChunks() + " chunks received");
        }

        long actualSize;
        try {
            actualSize = Files.size(session.tempFilePath());
        } catch (IOException e) {
            throw new BusinessLogicException(AppCode.CODE_404, "Upload session data missing: " + uploadId);
        }
        if (actualSize != session.fileSize()) {
            throw new BusinessLogicException(AppCode.CODE_400,
                    "Assembled file size mismatch: expected " + session.fileSize() + " but got " + actualSize);
        }

        Path targetDir = support.rootDir().resolve(session.relativeDir()).normalize();
        Path targetFile = support.resolveTargetFile(targetDir, session.storedFileName());
        try {
            Files.createDirectories(targetDir);
            // Cùng cây root-dir -> move là đổi tên tại chỗ (không copy lại), kể cả file rất lớn.
            Files.move(session.tempFilePath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Finalize chunked upload failed | uploadId: {} | {}", uploadId, e.getMessage(), e);
            throw new BusinessLogicException(AppCode.CODE_500, "Cannot finalize upload");
        }

        try {
            // Cũng như FileStorageServiceImpl#store: content-type client tự khai lúc init không đủ
            // tin cậy -> đối chiếu chữ ký thật của file vừa ghép xong trước khi tạo bản ghi upload_file.
            support.verifyMagicBytes(session.contentType(), targetFile);
        } catch (BusinessLogicException e) {
            deleteQuietly(targetFile);
            sessions.invalidate(uploadId);
            throw e;
        }

        UploadFile uploadFile = new UploadFile();
        uploadFile.setOriginalFileName(session.originalFileName());
        uploadFile.setStoredFileName(session.storedFileName());
        uploadFile.setFilePath(session.relativeDir() + "/" + session.storedFileName());
        uploadFile.setContentType(session.contentType());
        uploadFile.setFileCategory(session.category());
        uploadFile.setFileSize(actualSize);
        uploadFile.setCreatedAt(LocalDateTime.now());
        UploadFile saved = uploadFileRepository.save(uploadFile);

        // File tạm đã move đi nên listener xóa file tạm sẽ là no-op (deleteIfExists) — không sao.
        sessions.invalidate(uploadId);
        return saved;
    }

    @Override
    public void abort(String uploadId) {
        findSession(uploadId);
        sessions.invalidate(uploadId);
    }

    private ChunkUploadSession findSession(String uploadId) {
        ChunkUploadSession session = sessions.getIfPresent(uploadId);
        if (session == null) {
            throw new BusinessLogicException(AppCode.CODE_404, "Upload session not found or expired: " + uploadId);
        }
        return session;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Cannot delete temp upload file {}: {}", path, e.getMessage());
        }
    }

    private record ChunkUploadSession(
            String uploadId,
            String originalFileName,
            String contentType,
            FileCategory category,
            long fileSize,
            long chunkSize,
            int totalChunks,
            Path tempFilePath,
            String relativeDir,
            String storedFileName,
            Set<Integer> receivedChunks
    ) {
    }
}
