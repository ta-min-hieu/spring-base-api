package com.ringme.base.iam.controller;

import com.ringme.base.iam.dto.app.response.MenuResponse;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.enums.AppCode;
import com.ringme.base.iam.security.PermissionResource;
import com.ringme.base.iam.service.RbacMeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Menu/permission của CHÍNH người dùng đang đăng nhập — MỌI user đã xác thực đều gọi được (không
 * @RolesAllowed riêng), dùng để Angular dựng sidebar + bật/tắt hành động theo permission code sau khi login.
 */
@RestController
@RequestMapping("/v1/rbac/me")
@RequiredArgsConstructor
@Tag(name = "RBAC - Me", description = "Menu/permission của người dùng đang đăng nhập")
public class RbacMeController {

    private final RbacMeService rbacMeService;

    @Operation(summary = "Cây menu của tôi", description = "Hợp nhất menu từ tất cả role hiện có, dùng dựng sidebar")
    @GetMapping("/menus")
    public Response<List<MenuResponse>> getMyMenus() {
        return AppCode.CODE_200.getResponse(rbacMeService.getMyMenus());
    }

    @Operation(summary = "Permission của tôi", description = "Hợp nhất resource từ tất cả role hiện có")
    @GetMapping("/permissions")
    public Response<List<PermissionResource>> getMyPermissions() {
        return AppCode.CODE_200.getResponse(rbacMeService.getMyPermissions());
    }
}
