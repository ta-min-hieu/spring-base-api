package com.ringme.base.dto.app.response.iam;

import com.ringme.base.entity.iam.Menu;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.enums.iam.MenuType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MenuResponse {
    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String icon;
    private MenuType menuType;
    private Integer sortOrder;
    private Boolean visible;
    private CommonStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Builder.Default
    private List<MenuResponse> children = new ArrayList<>();

    public static MenuResponse from(Menu menu) {
        return MenuResponse.builder()
                .id(menu.getId())
                .parentId(menu.getParentId())
                .name(menu.getName())
                .path(menu.getPath())
                .component(menu.getComponent())
                .icon(menu.getIcon())
                .menuType(menu.getMenuType())
                .sortOrder(menu.getSortOrder())
                .visible(menu.getVisible())
                .status(menu.getStatus())
                .createdAt(menu.getCreatedAt())
                .updatedAt(menu.getUpdatedAt())
                .build();
    }

    /**
     * Dựng cây từ danh sách phẳng (nhóm theo parentId trong bộ nhớ, xem javadoc {@link Menu}) — dùng
     * chung cho MenuService (toàn bộ cây, quản trị) và RbacMeService (cây đã lọc theo role, /v1/rbac/me/menus).
     * Node có parentId trỏ tới 1 id KHÔNG có mặt trong flat list (vd bị lọc do role không thấy node cha)
     * thì tự trở thành node gốc trong cây trả về, tránh mất node con.
     */
    public static List<MenuResponse> buildTree(List<MenuResponse> flat) {
        Map<Long, MenuResponse> byId = new LinkedHashMap<>();
        for (MenuResponse node : flat) {
            node.setChildren(new ArrayList<>());
            byId.put(node.getId(), node);
        }

        List<MenuResponse> roots = new ArrayList<>();
        for (MenuResponse node : flat) {
            MenuResponse parent = node.getParentId() == null ? null : byId.get(node.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }

        sortTree(roots);
        return roots;
    }

    private static void sortTree(List<MenuResponse> nodes) {
        nodes.sort(Comparator.comparing(MenuResponse::getSortOrder, Comparator.nullsLast(Integer::compareTo)));
        for (MenuResponse node : nodes) {
            sortTree(node.getChildren());
        }
    }
}
