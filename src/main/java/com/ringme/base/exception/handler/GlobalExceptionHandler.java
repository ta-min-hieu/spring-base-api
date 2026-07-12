package com.ringme.base.exception.handler;

import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Log4j2
public class GlobalExceptionHandler {
    // Gom đầy đủ: mỗi field giữ DANH SÁCH message (1 field có thể vi phạm nhiều ràng buộc).
    private void addError(Map<String, List<String>> errors, String field, String message) {
        errors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
    }

    // Xử lý khi @Valid lúc Binding vào 1 object container như @RequestBody và @ModelAttribute validation failed.
    // MethodArgumentNotValidException (lỗi @Valid @RequestBody) kế thừa BindException nên cũng vào đây.
    @ExceptionHandler(BindException.class)
    public ResponseEntity<?> handleBindException(BindException ex) {
        log.warn("VALIDATION ERROR: {}", ex.getMessage());
        BindingResult result = ex.getBindingResult();
        Map<String, List<String>> errors = new LinkedHashMap<>();

        // Lỗi cấp trường (field constraint: @NotBlank, @Size, ...)
        for (FieldError error : result.getFieldErrors()) {
            addError(errors, error.getField(), error.getDefaultMessage());
        }

        // Lỗi cấp object/global (ràng buộc chéo trường: validator cấp class, @ScriptAssert, ...).
        // Những lỗi này KHÔNG nằm trong getFieldErrors() nên phải duyệt riêng kẻo bị mất.
        for (ObjectError error : result.getGlobalErrors()) {
            addError(errors, error.getObjectName(), error.getDefaultMessage());
        }

        return ResponseEntity.badRequest().body(AppCode.CODE_400.getResponse(errors));
    }

    // Xử lý khi có @Validated trên bean/@Service và vi phạm ràng buộc validation ở tham số phương thức.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("VALIDATION ERROR: {}", ex.getMessage());
        Map<String, List<String>> errors = new LinkedHashMap<>();

        // Duyệt qua các lỗi validation cho từng tham số bị vi phạm
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            Iterator<Path.Node> iterator = violation.getPropertyPath().iterator();
            Path.Node lastNode = null;

            while (iterator.hasNext()) {
                lastNode = iterator.next();
            }

            String fieldName = (lastNode != null) ? lastNode.getName() : "unknown";
            addError(errors, fieldName, violation.getMessage());
        }

        return ResponseEntity.badRequest().body(AppCode.CODE_400.getResponse(errors));
    }

    // Xử lý validation cấp tham số controller (@RequestParam/@PathVariable có @Min, @NotBlank, ...).
    // Từ Spring 6.1, MVC ném HandlerMethodValidationException thay cho ConstraintViolationException.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> handleHandlerMethodValidationException(HandlerMethodValidationException ex) {
        log.warn("VALIDATION ERROR: {}", ex.getMessage());
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (ParameterValidationResult paramError : ex.getParameterValidationResults()) {
            String fieldName = paramError.getMethodParameter().getParameterName();
            if (fieldName == null) {
                // -parameters không bật hoặc lỗi cross-parameter -> fallback theo vị trí tham số
                fieldName = "arg" + paramError.getMethodParameter().getParameterIndex();
            }
            // Một tham số có thể có nhiều lỗi -> gom hết, phòng trường hợp list rỗng.
            List<? extends MessageSourceResolvable> resolvable = paramError.getResolvableErrors();
            if (resolvable.isEmpty()) {
                addError(errors, fieldName, "Giá trị không hợp lệ");
            } else {
                for (MessageSourceResolvable r : resolvable) {
                    addError(errors, fieldName, r.getDefaultMessage());
                }
            }
        }
        return ResponseEntity.badRequest().body(AppCode.CODE_400.getResponse(errors));
    }

    // Body request rỗng hoặc sai định dạng JSON (không deserialize được) -> 400 thay vì 500.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("BAD REQUEST: {}", ex.getMessage());
        Map<String, List<String>> errors = new LinkedHashMap<>();
        addError(errors, "body", "Nội dung request rỗng hoặc sai định dạng JSON");
        return ResponseEntity.badRequest().body(AppCode.CODE_400.getResponse(errors));
    }

    // Thiếu tham số @RequestParam bắt buộc -> 400 thay vì 500.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        log.warn("BAD REQUEST: {}", ex.getMessage());
        Map<String, List<String>> errors = new LinkedHashMap<>();
        addError(errors, ex.getParameterName(), "Tham số bắt buộc bị thiếu");
        return ResponseEntity.badRequest().body(AppCode.CODE_400.getResponse(errors));
    }

    // Sai kiểu dữ liệu ở @PathVariable/@RequestParam (vd: chữ ở chỗ cần số) -> 400 thay vì 500.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("BAD REQUEST: {}", ex.getMessage());
        Map<String, List<String>> errors = new LinkedHashMap<>();
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "hợp lệ";
        addError(errors, ex.getName(), "Giá trị '" + ex.getValue() + "' không đúng kiểu " + expectedType);
        return ResponseEntity.badRequest().body(AppCode.CODE_400.getResponse(errors));
    }

    // Sai quyền (method security @RolesAllowed/@PreAuthorize ném AccessDeniedException/AuthorizationDeniedException
    // ngay khi gọi controller) -> phải là 403, không để rơi vào catch-all thành 500.
    // Lưu ý: lỗi 401 (thiếu/sai token) đã được JwtAuthenticationEntryPoint xử lý ở tầng filter, không tới đây.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException e) {
        log.warn("FORBIDDEN: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(AppCode.CODE_403.getResponse());
    }

    // Các lỗi mang sẵn HTTP status của Spring -> tôn trọng status gốc thay vì ép về 500.
    // - ErrorResponseException: cha của ResponseStatusException (lỗi do code chủ động ném).
    // - NoResourceFoundException: route không tồn tại (404); KHÔNG kế thừa ErrorResponseException
    //   (nó extends ServletException) nên phải khai báo handler riêng.
    // HandlerMethodValidationException tuy cũng là con của ResponseStatusException nhưng đã có handler
    // riêng (Spring chọn handler cụ thể hơn) nên không lọt vào đây.
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<?> handleErrorResponse(ErrorResponseException e) {
        return respondWithStatus(e.getStatusCode(), e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(NoResourceFoundException e) {
        return respondWithStatus(e.getStatusCode(), e.getMessage());
    }

    // Trả response theo đúng HTTP status, map code body từ status (vd 404 -> CODE_404).
    private ResponseEntity<?> respondWithStatus(HttpStatusCode status, String message) {
        log.warn("HTTP {}: {}", status.value(), message);
        return ResponseEntity.status(status)
                .body(AppCode.initFromCode(String.valueOf(status.value())).getResponse());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        log.error("ERROR: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AppCode.CODE_500.getResponse());
    }

    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<?> handleBusinessLogicException(BusinessLogicException e) {
        if (e.isLog()) {
            if (e.isTraceStackFull()) {
                log.error("HANDLED ERROR: {}", e.getCode(), e);
            } else {
                StackTraceElement stackTraceElm = e.getStackTrace()[0];
                log.error("HANDLED ERROR: {} - {} | file: {} | method: {} | line: {}", e.getCode(), e.getCode().getMessage(), stackTraceElm.getFileName(), stackTraceElm.getMethodName(), stackTraceElm.getLineNumber());
            }
        }

        return ResponseEntity.status(e.getCode().toHttpStatus())
                .body(e.getCode().getResponse(null, null,
                        e.getMessage() == null ? e.getCode().getMessageLang() : e.getMessage()));
    }
}
