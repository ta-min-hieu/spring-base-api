package com.ringme.base.iam.config;

import com.ringme.base.enums.AppCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Ghi JSON 403 nhất quán (AppCode.CODE_403) cho AccessDeniedException bị ném từ FILTER (vd
 * DynamicPermissionFilter) — khác với AccessDeniedException ném từ @RolesAllowed/@PreAuthorize
 * TRONG lúc DispatcherServlet gọi controller, vốn đã được GlobalExceptionHandler.handleAccessDenied
 * xử lý (@RestControllerAdvice chỉ bắt được exception trong phạm vi DispatcherServlet, không bắt
 * được exception ném từ filter chạy TRƯỚC nó — cần ExceptionTranslationFilter + handler này).
 */
@Component
@RequiredArgsConstructor
public class RbacAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                        HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");

        PrintWriter writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(AppCode.CODE_403.getResponse()));
    }
}
