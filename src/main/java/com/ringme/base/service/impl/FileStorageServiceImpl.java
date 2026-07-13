package com.ringme.base.service.impl;

import com.ringme.base.entity.UploadFile;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.FileCategory;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.UploadFileRepository;
import com.ringme.base.service.FileStorageService;
import com.ringme.base.service.support.FileStorageSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Log4j2
public class FileStorageServiceImpl implements FileStorageService {

    private final UploadFileRepository uploadFileRepository;
    private final FileStorageSupport support;

    @Override
    @Transactional
    public UploadFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessLogicException(AppCode.CODE_400, "File is empty");
        }

        FileCategory category = support.detectCategory(file.getContentType());
        support.validateSize(category, file.getSize());
        support.validateContentType(category, file.getContentType());

        String relativeDir = support.relativeDirFor(category, LocalDate.now());
        String storedFileName = support.newStoredFileName(file.getOriginalFilename());

        Path targetDir = support.rootDir().resolve(relativeDir).normalize();
        Path targetFile = support.resolveTargetFile(targetDir, storedFileName);

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetFile);
        } catch (IOException e) {
            log.error("Store file failed: {}", e.getMessage(), e);
            throw new BusinessLogicException(AppCode.CODE_500, "Cannot store file");
        }

        UploadFile uploadFile = new UploadFile();
        uploadFile.setOriginalFileName(file.getOriginalFilename());
        uploadFile.setStoredFileName(storedFileName);
        uploadFile.setFilePath(relativeDir + "/" + storedFileName);
        uploadFile.setContentType(file.getContentType());
        uploadFile.setFileCategory(category);
        uploadFile.setFileSize(file.getSize());
        uploadFile.setCreatedAt(LocalDateTime.now());

        return uploadFileRepository.save(uploadFile);
    }

    @Override
    public UploadFile findById(Long id) {
        return uploadFileRepository.findById(id)
                .orElseThrow(() -> new BusinessLogicException(AppCode.CODE_404, "File not found: " + id));
    }

    @Override
    public Resource loadAsResource(UploadFile uploadFile) {
        Path file = support.rootDir().resolve(uploadFile.getFilePath()).normalize();
        Resource resource;
        try {
            resource = new UrlResource(file.toUri());
        } catch (MalformedURLException e) {
            throw new BusinessLogicException(AppCode.CODE_404, "File not found: " + uploadFile.getId());
        }
        if (!resource.exists() || !resource.isReadable()) {
            throw new BusinessLogicException(AppCode.CODE_404, "File not found: " + uploadFile.getId());
        }
        return resource;
    }

    @Override
    @Transactional
    public void delete(UploadFile uploadFile) {
        Path file = support.rootDir().resolve(uploadFile.getFilePath()).normalize();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Cannot delete physical file {}: {}", file, e.getMessage());
        }
        uploadFileRepository.delete(uploadFile);
    }
}
