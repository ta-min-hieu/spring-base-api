package com.ringme.base.controller.v1;

import com.ringme.base.dto.app.request.MenuRequest;
import com.ringme.base.dto.app.response.MenuResponse;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.enums.AppCode;
import com.ringme.base.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Quản trị Menu (cây điều hướng UI, ĐỘC LẬP với Permission — xem javadoc entity Menu). */
@RestController
@RequestMapping("/v1/rbac/menus")
@RequiredArgsConstructor
@RolesAllowed("ADMIN")
@Tag(name = "RBAC - Menu", description = "Quản lý cây menu điều hướng UI")
public class MenuController {

    private final MenuService menuService;

    @Operation(summary = "Toàn bộ cây menu", description = "Không lọc theo role — dùng cho trang quản trị Menu")
    @GetMapping
    public Response<List<MenuResponse>> tree() {
        return AppCode.CODE_200.getResponse(menuService.tree());
    }

    @Operation(summary = "Chi tiết 1 menu node")
    @GetMapping("/{id}")
    public Response<MenuResponse> getById(@PathVariable Long id) {
        return AppCode.CODE_200.getResponse(menuService.getById(id));
    }

    @Operation(summary = "Tạo menu node")
    @PostMapping
    public Response<MenuResponse> create(@Valid @RequestBody MenuRequest request) {
        return AppCode.CODE_200.getResponse(menuService.create(request));
    }

    @Operation(summary = "Cập nhật menu node")
    @PutMapping("/{id}")
    public Response<MenuResponse> update(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
        return AppCode.CODE_200.getResponse(menuService.update(id, request));
    }

    @Operation(summary = "Xóa menu node", description = "Xóa cả các menu con (ON DELETE CASCADE ở tầng DB)")
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return AppCode.CODE_200.getResponse();
    }
}
