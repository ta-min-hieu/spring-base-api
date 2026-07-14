package com.ringme.base.service;

import com.ringme.base.dto.app.response.MenuResponse;

import java.util.List;

/**
 * Nguồn duy nhất để tra cứu "role_key này thấy menu nào" — có cache (Caffeine/Redis tuỳ cấu hình, xem
 * KeyCache.ROLE_MENUS) để RbacMeService (GET /v1/rbac/me/menus) không phải query DB trên mỗi request.
 * Trả về danh sách PHẲNG (không children) — gộp + dựng cây được thực hiện ở tầng gọi (RbacMeService)
 * sau khi hợp nhất menu từ NHIỀU role của user.
 */
public interface MenuCacheService {

    List<MenuResponse> getMenusForRole(String roleKey);

    /** Evict cache của 1 role — gọi khi role_menu của role đó thay đổi để có hiệu lực ngay. */
    void evictRole(String roleKey);

    /** Evict TOÀN BỘ cache — gọi khi 1 Menu (tên/route/status) đổi, ảnh hưởng nhiều role cùng lúc. */
    void evictAll();
}
