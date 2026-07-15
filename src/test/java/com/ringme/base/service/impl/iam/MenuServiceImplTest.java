package com.ringme.base.service.impl.iam;

import com.ringme.base.dto.app.request.iam.MenuRequest;
import com.ringme.base.dto.app.response.iam.MenuResponse;
import com.ringme.base.entity.iam.Menu;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.enums.iam.MenuType;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.MenuRepository;
import com.ringme.base.service.iam.MenuCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần cho MenuServiceImpl: chống vòng lặp cha-con khi đổi parentId (bug thật đã gặp: dùng
 * Collectors.toMap ném NullPointerException ngay khi có node gốc parentId=null, phải dùng HashMap thủ
 * công), và update() giữ nguyên sortOrder/visible/status khi request bỏ trống.
 */
class MenuServiceImplTest {

    private MenuRepository menuRepository;
    private MenuCacheService menuCacheService;
    private MenuServiceImpl service;

    @BeforeEach
    void setUp() {
        menuRepository = mock(MenuRepository.class);
        menuCacheService = mock(MenuCacheService.class);
        service = new MenuServiceImpl(menuRepository, menuCacheService);

        when(menuRepository.save(any(Menu.class))).thenAnswer(inv -> {
            Menu m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(99L);
            }
            return m;
        });
    }

    private Menu menu(Long id, Long parentId) {
        Menu m = new Menu();
        m.setId(id);
        m.setParentId(parentId);
        m.setName("Menu " + id);
        m.setMenuType(MenuType.MENU);
        m.setSortOrder(0);
        m.setVisible(true);
        m.setStatus(CommonStatus.ACTIVE);
        return m;
    }

    @Test
    void create_withRootParent_doesNotThrow_evenThoughOtherMenusHaveNullParentId() {
        // Danh sách có node gốc (parentId=null) — trước đây Collectors.toMap ném NPE ngay ở bước này.
        when(menuRepository.findAll()).thenReturn(List.of(menu(1L, null), menu(2L, 1L)));

        MenuResponse response = service.create(MenuRequest.builder()
                .parentId(1L).name("Child").menuType(MenuType.MENU).build());

        assertEquals("Child", response.getName());
    }

    @Test
    void update_rejectsSelfAsOwnParent() {
        Menu existing = menu(1L, null);
        when(menuRepository.findById(1L)).thenReturn(Optional.of(existing));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.update(1L, MenuRequest.builder().parentId(1L).name("X").menuType(MenuType.MENU).build()));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void update_rejectsDescendantAsNewParent() {
        // Cây: 1 (gốc) -> 2 -> 3. Gán parentId của node 1 = node 3 (con cháu của chính nó) phải bị chặn.
        Menu root = menu(1L, null);
        Menu child = menu(2L, 1L);
        Menu grandchild = menu(3L, 2L);
        when(menuRepository.findById(1L)).thenReturn(Optional.of(root));
        when(menuRepository.findAll()).thenReturn(List.of(root, child, grandchild));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.update(1L, MenuRequest.builder().parentId(3L).name("root").menuType(MenuType.DIRECTORY).build()));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void update_rejectsNonExistentParent() {
        Menu existing = menu(1L, null);
        when(menuRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(menuRepository.findAll()).thenReturn(List.of(existing));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.update(1L, MenuRequest.builder().parentId(999L).name("X").menuType(MenuType.MENU).build()));
        assertEquals(AppCode.CODE_400, ex.getCode());
    }

    @Test
    void update_omittedFields_preserveExistingValues() {
        Menu existing = menu(1L, null);
        existing.setSortOrder(7);
        existing.setVisible(false);
        existing.setStatus(CommonStatus.DISABLED);
        when(menuRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(menuRepository.findAll()).thenReturn(List.of(existing));

        service.update(1L, MenuRequest.builder().name("Renamed").menuType(MenuType.MENU).build());

        assertEquals(7, existing.getSortOrder());
        assertEquals(false, existing.getVisible());
        assertEquals(CommonStatus.DISABLED, existing.getStatus());
        assertEquals("Renamed", existing.getName());
    }

    @Test
    void tree_buildsNestedStructureFromFlatList() {
        when(menuRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(menu(1L, null), menu(2L, 1L)));

        List<MenuResponse> tree = service.tree();

        assertEquals(1, tree.size());
        assertEquals(1L, tree.get(0).getId());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals(2L, tree.get(0).getChildren().get(0).getId());
    }

    @Test
    void getById_notFound_throws404() {
        when(menuRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.getById(99L));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }
}
