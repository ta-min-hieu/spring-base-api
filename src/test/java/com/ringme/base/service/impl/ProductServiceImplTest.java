package com.ringme.base.service.impl;

import com.ringme.base.dto.app.request.ProductRequest;
import com.ringme.base.dto.app.response.ProductResponse;
import com.ringme.base.entity.Product;
import com.ringme.base.entity.ProductUploadFile;
import com.ringme.base.entity.UploadFile;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.FileCategory;
import com.ringme.base.enums.ProductStatus;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.ProductRepository;
import com.ringme.base.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần (không Spring context) cho ProductServiceImpl: mock ProductRepository +
 * FileStorageService, kiểm tra logic gắn/gỡ file, sortOrder, và các trường hợp 404.
 */
class ProductServiceImplTest {

    private ProductRepository productRepository;
    private FileStorageService fileStorageService;
    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        fileStorageService = mock(FileStorageService.class);
        service = new ProductServiceImpl(productRepository, fileStorageService);

        // save() giả lập hành vi IDENTITY của Oracle/Hibernate: gán id rồi trả lại đúng instance.
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(1L);
            }
            return p;
        });
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProductRequest sampleRequest() {
        return ProductRequest.builder()
                .name("Ao thun")
                .price(new BigDecimal("199.99"))
                .stock(10)
                .category("clothes")
                .tags(List.of("summer", "cotton"))
                .description("mo ta")
                .status(ProductStatus.ACTIVE)
                .featured(true)
                .build();
    }

    private UploadFile uploadFile(Long id) {
        UploadFile f = new UploadFile();
        f.setId(id);
        f.setOriginalFileName("f" + id + ".png");
        f.setStoredFileName("uuid-" + id + ".png");
        f.setFilePath("images/2026/01/01/uuid-" + id + ".png");
        f.setContentType("image/png");
        f.setFileCategory(FileCategory.IMAGE);
        f.setFileSize(123L);
        return f;
    }

    // ===== create =====

    @Test
    void create_mapsRequestFieldsOntoNewProduct_noFiles() {
        ProductResponse response = service.create(sampleRequest(), null, null);

        assertEquals("Ao thun", response.getName());
        assertEquals(new BigDecimal("199.99"), response.getPrice());
        assertEquals(10, response.getStock());
        assertEquals("clothes", response.getCategory());
        assertTrue(response.getTags().containsAll(List.of("summer", "cotton")));
        assertEquals(ProductStatus.ACTIVE, response.getStatus());
        assertTrue(response.getFeatured());
        assertTrue(response.getFiles().isEmpty());
        verify(fileStorageService, never()).store(any());
        verify(fileStorageService, never()).findById(anyLong());
    }

    @Test
    void create_withDirectFiles_storesEachAndAssignsIncreasingSortOrder() {
        MultipartFile f1 = mock(MultipartFile.class);
        MultipartFile f2 = mock(MultipartFile.class);
        when(f1.isEmpty()).thenReturn(false);
        when(f2.isEmpty()).thenReturn(false);
        when(fileStorageService.store(f1)).thenReturn(uploadFile(10L));
        when(fileStorageService.store(f2)).thenReturn(uploadFile(11L));

        ProductResponse response = service.create(sampleRequest(), List.of(f1, f2), null);

        assertEquals(2, response.getFiles().size());
        assertEquals(10L, response.getFiles().get(0).getId());
        assertEquals(11L, response.getFiles().get(1).getId());
        verify(fileStorageService, times(1)).store(f1);
        verify(fileStorageService, times(1)).store(f2);
    }

    @Test
    void create_skipsNullAndEmptyMultipartFileEntries() {
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);
        java.util.ArrayList<MultipartFile> files = new java.util.ArrayList<>();
        files.add(empty);
        files.add(null);

        ProductResponse response = service.create(sampleRequest(), files, null);

        assertTrue(response.getFiles().isEmpty());
        verify(fileStorageService, never()).store(any());
    }

    @Test
    void create_withFileIds_linksAlreadyUploadedFiles() {
        when(fileStorageService.findById(20L)).thenReturn(uploadFile(20L));

        ProductResponse response = service.create(sampleRequest(), null, List.of(20L));

        assertEquals(1, response.getFiles().size());
        assertEquals(20L, response.getFiles().get(0).getId());
        verify(fileStorageService, never()).store(any());
    }

    // ===== update =====

    @Test
    void update_throwsNotFound_whenProductMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.update(99L, sampleRequest(), null, null));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }

    @Test
    void update_appendsNewFiles_continuingSortOrderAfterExisting() {
        Product existing = new Product();
        existing.setId(5L);
        ProductUploadFile existingLink = new ProductUploadFile();
        existingLink.setProduct(existing);
        existingLink.setUploadFile(uploadFile(1L));
        existingLink.setSortOrder(0);
        existing.getUploadFiles().add(existingLink);
        when(productRepository.findById(5L)).thenReturn(Optional.of(existing));

        when(fileStorageService.findById(30L)).thenReturn(uploadFile(30L));

        ProductResponse response = service.update(5L, sampleRequest(), null, List.of(30L));

        assertEquals(2, response.getFiles().size());
        // File cũ vẫn còn (không bị xóa khi update), file mới nối vào cuối.
        assertEquals(1L, response.getFiles().get(0).getId());
        assertEquals(30L, response.getFiles().get(1).getId());
        assertEquals(1, existing.getUploadFiles().get(1).getSortOrder());
    }

    // ===== getById / list =====

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(productRepository.findById(7L)).thenReturn(Optional.empty());
        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.getById(7L));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }

    @Test
    void list_blankName_delegatesToFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.findAll(pageable)).thenReturn(page);

        service.list("  ", pageable);

        verify(productRepository, times(1)).findAll(pageable);
        verify(productRepository, never()).findByNameContainingIgnoreCase(any(), any());
    }

    @Test
    void list_withName_delegatesToNameSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.findByNameContainingIgnoreCase(eq("ao"), eq(pageable))).thenReturn(page);

        service.list("ao", pageable);

        verify(productRepository, times(1)).findByNameContainingIgnoreCase("ao", pageable);
        verify(productRepository, never()).findAll(pageable);
    }

    // ===== delete =====

    @Test
    void delete_deletesProductThenCascadeDeletesEachAttachedFile() {
        Product product = new Product();
        product.setId(8L);
        ProductUploadFile link1 = new ProductUploadFile();
        link1.setUploadFile(uploadFile(1L));
        ProductUploadFile link2 = new ProductUploadFile();
        link2.setUploadFile(uploadFile(2L));
        product.getUploadFiles().add(link1);
        product.getUploadFiles().add(link2);
        when(productRepository.findById(8L)).thenReturn(Optional.of(product));

        service.delete(8L);

        verify(productRepository, times(1)).delete(product);
        verify(productRepository, times(1)).flush();
        verify(fileStorageService, times(1)).delete(link1.getUploadFile());
        verify(fileStorageService, times(1)).delete(link2.getUploadFile());
    }

    // ===== removeFile =====

    @Test
    void removeFile_throwsNotFound_whenProductMissing() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.removeFile(1L, 2L));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }

    @Test
    void removeFile_throwsNotFound_whenFileNotAttachedToProduct() {
        Product product = new Product();
        product.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.removeFile(1L, 999L));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }

    @Test
    void removeFile_removesLinkFromCollectionAndDeletesUploadFile() {
        Product product = new Product();
        product.setId(1L);
        UploadFile file = uploadFile(2L);
        ProductUploadFile link = new ProductUploadFile();
        link.setProduct(product);
        link.setUploadFile(file);
        product.getUploadFiles().add(link);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        service.removeFile(1L, 2L);

        assertTrue(product.getUploadFiles().isEmpty());
        verify(productRepository, times(1)).saveAndFlush(product);
        verify(fileStorageService, times(1)).delete(file);
    }
}
