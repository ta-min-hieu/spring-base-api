package com.ringme.base.service.impl.iam;

import com.ringme.base.dto.app.request.iam.PermissionRequest;
import com.ringme.base.entity.iam.Permission;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.PermissionRepository;
import com.ringme.base.service.iam.PermissionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần cho PermissionServiceImpl: trùng code/resource (method+urlPattern) bị chặn cả lúc
 * tạo lẫn sửa (trừ chính bản ghi đang sửa), update() giữ nguyên status khi không gửi, và cache toàn
 * bộ (evictAll) bị xoá sau mỗi lần create/update/delete vì 1 permission có thể ảnh hưởng nhiều role.
 */
class PermissionServiceImplTest {

    private PermissionRepository permissionRepository;
    private PermissionCacheService permissionCacheService;
    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        permissionRepository = mock(PermissionRepository.class);
        permissionCacheService = mock(PermissionCacheService.class);
        service = new PermissionServiceImpl(permissionRepository, permissionCacheService);

        when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> {
            Permission p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(1L);
            }
            return p;
        });
    }

    private PermissionRequest.PermissionRequestBuilder validRequest() {
        return PermissionRequest.builder()
                .code("product:list").name("List products").httpMethod("GET").urlPattern("/v1/products");
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(permissionRepository.existsByCode("product:list")).thenReturn(true);

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.create(validRequest().build()));
        assertEquals(AppCode.CODE_400, ex.getCode());
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void create_rejectsDuplicateResource() {
        when(permissionRepository.existsByCode("product:list")).thenReturn(false);
        when(permissionRepository.existsByHttpMethodAndUrlPattern("GET", "/v1/products")).thenReturn(true);

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.create(validRequest().build()));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void create_defaultsStatusToActive_whenOmitted() {
        var response = service.create(validRequest().build());

        assertEquals(CommonStatus.ACTIVE, response.getStatus());
    }

    @Test
    void create_evictsEntireCache() {
        service.create(validRequest().build());

        verify(permissionCacheService).evictAll();
    }

    @Test
    void update_omittedStatus_preservesExistingValue() {
        Permission existing = new Permission();
        existing.setId(1L);
        existing.setCode("product:list");
        existing.setName("List products");
        existing.setHttpMethod("GET");
        existing.setUrlPattern("/v1/products");
        existing.setStatus(CommonStatus.DISABLED);
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(permissionRepository.findByCode("product:list")).thenReturn(Optional.of(existing));
        when(permissionRepository.findByHttpMethodAndUrlPattern("GET", "/v1/products")).thenReturn(Optional.of(existing));

        service.update(1L, validRequest().name("List products v2").build());

        assertEquals(CommonStatus.DISABLED, existing.getStatus());
        assertEquals("List products v2", existing.getName());
    }

    @Test
    void update_conflictingResource_excludesSelf() {
        Permission existing = new Permission();
        existing.setId(1L);
        existing.setCode("product:list");
        existing.setHttpMethod("GET");
        existing.setUrlPattern("/v1/products");
        existing.setStatus(CommonStatus.ACTIVE);
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(existing));
        // findByCode/findByHttpMethodAndUrlPattern trả về CHÍNH bản ghi đang sửa -> không phải xung đột.
        when(permissionRepository.findByCode("product:list")).thenReturn(Optional.of(existing));
        when(permissionRepository.findByHttpMethodAndUrlPattern("GET", "/v1/products")).thenReturn(Optional.of(existing));

        // Không được ném exception vì bản ghi trùng chính là bản ghi đang sửa.
        service.update(1L, validRequest().build());

        verify(permissionRepository, times(1)).save(existing);
    }

    @Test
    void update_conflictingResource_withDifferentPermission_throws() {
        Permission existing = new Permission();
        existing.setId(1L);
        existing.setCode("product:list");
        existing.setHttpMethod("GET");
        existing.setUrlPattern("/v1/products");
        existing.setStatus(CommonStatus.ACTIVE);
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(permissionRepository.findByCode("product:list")).thenReturn(Optional.of(existing));

        Permission other = new Permission();
        other.setId(2L);
        when(permissionRepository.findByHttpMethodAndUrlPattern("GET", "/v1/products")).thenReturn(Optional.of(other));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.update(1L, validRequest().build()));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void delete_evictsEntireCache() {
        Permission existing = new Permission();
        existing.setId(1L);
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(permissionRepository).delete(existing);
        verify(permissionCacheService).evictAll();
    }
}
