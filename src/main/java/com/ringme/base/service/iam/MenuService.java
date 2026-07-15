package com.ringme.base.service.iam;

import com.ringme.base.dto.app.request.iam.MenuRequest;
import com.ringme.base.dto.app.response.iam.MenuResponse;

import java.util.List;

public interface MenuService {

    /** Toàn bộ cây menu (không lọc theo role — dùng cho trang quản trị Menu). */
    List<MenuResponse> tree();

    MenuResponse getById(Long id);

    MenuResponse create(MenuRequest request);

    MenuResponse update(Long id, MenuRequest request);

    void delete(Long id);
}
