package com.ringme.base.utils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Che (mask) dữ liệu nhạy cảm trước khi ghi log: giá trị header (Authorization, Cookie, ...) và
 * các field nhạy cảm trong body JSON (password, accessToken, refreshToken, ...). Tránh rò rỉ
 * token/mật khẩu vào file log.
 */
public final class LogMasker {

    public static final String MASK = "***";

    // Jackson 3: JsonMapper là immutable, khởi tạo qua builder.
    private static final JsonMapper JSON = JsonMapper.builder().build();

    // Tên header nhạy cảm (so sánh dạng chữ thường).
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");

    // Tên field nhạy cảm trong body (so sánh sau khi chuẩn hoá: bỏ ký tự không phải chữ/số, về chữ thường).
    // Nhờ chuẩn hoá nên "access_token", "accessToken", "access-token" đều khớp "accesstoken".
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "pass", "pwd", "token", "accesstoken", "refreshtoken",
            "secret", "appsecurity", "otp", "pin");

    private LogMasker() {
    }

    /** Trả về MASK nếu header thuộc nhóm nhạy cảm, ngược lại giữ nguyên giá trị. */
    public static String maskHeaderValue(String headerName, String value) {
        if (headerName == null) {
            return value;
        }
        return isSensitiveHeader(headerName) ? MASK : value;
    }

    public static boolean isSensitiveHeader(String headerName) {
        return headerName != null && SENSITIVE_HEADERS.contains(headerName.toLowerCase());
    }

    public static boolean isSensitiveField(String field) {
        if (field == null) {
            return false;
        }
        return SENSITIVE_FIELDS.contains(field.toLowerCase().replaceAll("[^a-z0-9]", ""));
    }

    /**
     * Che các field nhạy cảm trong body JSON (đệ quy cả object lồng nhau và mảng).
     * Nếu body không phải JSON hợp lệ thì giữ nguyên (không cố đoán).
     */
    public static String maskJsonBody(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            JsonNode root = JSON.readTree(body);
            maskNode(root);
            return JSON.writeValueAsString(root);
        } catch (Exception e) {
            return body;
        }
    }

    private static void maskNode(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            // Gom tên field trước để tránh sửa trong lúc duyệt.
            // Jackson 3: fieldNames() được thay bằng propertyNames() trả về Collection.
            List<String> fields = new ArrayList<>(obj.propertyNames());
            for (String f : fields) {
                if (isSensitiveField(f)) {
                    obj.put(f, MASK);
                } else {
                    maskNode(obj.get(f));
                }
            }
        } else if (node instanceof ArrayNode arr) {
            arr.forEach(LogMasker::maskNode);
        }
    }
}
