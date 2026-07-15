package com.ringme.base.service.impl.iam;

import com.ringme.base.dto.app.request.iam.MenuRequest;
import com.ringme.base.dto.app.response.iam.MenuResponse;
import com.ringme.base.entity.iam.Menu;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.MenuRepository;
import com.ringme.base.service.iam.MenuCacheService;
import com.ringme.base.service.iam.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final MenuRepository menuRepository;
    private final MenuCacheService menuCacheService;

    @Override
    public List<MenuResponse> tree() {
        List<MenuResponse> flat = menuRepository.findAllByOrderBySortOrderAsc().stream()
                .map(MenuResponse::from)
                .toList();
        return MenuResponse.buildTree(flat);
    }

    @Override
    public MenuResponse getById(Long id) {
        return MenuResponse.from(findEntity(id));
    }

    @Override
    @Transactional
    public MenuResponse create(MenuRequest request) {
        Menu menu = new Menu();
        applyFields(menu, request, null, 0, Boolean.TRUE, CommonStatus.ACTIVE);
        MenuResponse response = MenuResponse.from(menuRepository.save(menu));
        menuCacheService.evictAll();
        return response;
    }

    @Override
    @Transactional
    public MenuResponse update(Long id, MenuRequest request) {
        Menu menu = findEntity(id);
        // Field null (sortOrder/visible/status) nghĩa là client không gửi -> GIỮ NGUYÊN giá trị hiện
        // có, không phải "reset về default của create()" (vd 1 menu đang ẩn/DISABLED bị vô tình bật
        // lại visible=true/status=ACTIVE chỉ vì PUT thiếu field).
        applyFields(menu, request, id, menu.getSortOrder(), menu.getVisible(), menu.getStatus());
        MenuResponse response = MenuResponse.from(menuRepository.save(menu));
        menuCacheService.evictAll();
        return response;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Menu menu = findEntity(id);
        // Menu con bị xoá theo ON DELETE CASCADE ở tầng DB (fk_menu_parent, xem sql/oracle/iam.sql).
        menuRepository.delete(menu);
        menuCacheService.evictAll();
    }

    private void applyFields(Menu menu, MenuRequest request, Long selfId,
                              Integer sortOrderIfNull, Boolean visibleIfNull, CommonStatus statusIfNull) {
        validateParent(request.getParentId(), selfId);
        menu.setParentId(request.getParentId());
        menu.setName(request.getName());
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setIcon(request.getIcon());
        menu.setMenuType(request.getMenuType());
        menu.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : sortOrderIfNull);
        menu.setVisible(request.getVisible() != null ? request.getVisible() : visibleIfNull);
        menu.setStatus(request.getStatus() != null ? request.getStatus() : statusIfNull);
    }

    /** parentId phải tồn tại, và (khi update) KHÔNG được là chính node đó hoặc 1 trong các node con của nó. */
    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BusinessLogicException(AppCode.CODE_400, "Menu không thể là cha của chính nó: " + parentId);
        }
        // KHÔNG dùng Collectors.toMap: Menu.parentId=null (node gốc) làm nó ném NullPointerException
        // (HashMap.merge bên trong Collectors.toMap không chấp nhận value null) — dùng putAll thủ công.
        Map<Long, Long> parentById = new HashMap<>();
        menuRepository.findAll().forEach(menu -> parentById.put(menu.getId(), menu.getParentId()));
        if (!parentById.containsKey(parentId)) {
            throw new BusinessLogicException(AppCode.CODE_400, "Parent menu not found: " + parentId);
        }
        if (selfId != null) {
            for (Long cursor = parentById.get(parentId); cursor != null; cursor = parentById.get(cursor)) {
                if (cursor.equals(selfId)) {
                    throw new BusinessLogicException(AppCode.CODE_400,
                            "Không thể gán parentId là 1 node con của chính node đang sửa: " + parentId);
                }
            }
        }
    }

    private Menu findEntity(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new BusinessLogicException(AppCode.CODE_404, "Menu not found: " + id));
    }
}
