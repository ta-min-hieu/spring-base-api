package com.ringme.base.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ringme.base.context.RequestContextHolder;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.service.common.MultiLangManager;
import com.ringme.base.utils.BeanUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;

@Log4j2
public enum AppCode {
    // Các mã response chung của ứng dụng. Mã nghiệp vụ riêng của từng dự án nên định nghĩa
    // ở một enum riêng trong dự án sử dụng, không thêm vào đây.
    CODE_200("200", "Success"),
    CODE_400("400", "Bad request"),
    CODE_401("401", "Verify failed"),
    CODE_402("402", "Validated failed"),
    CODE_403("403", "Forbidden"),
    CODE_404("404", "Not found"),
    CODE_429("429", "Too many requests"),
    CODE_500("500", "Error in server"),
    UNKNOWN("0", "Unknown error"),

    // Các mã response cho xác thực
    TOKEN_IS_NOT_REFRESH("401", "Token is not refresh"),
    TOKEN_INVALID("401", "Token is invalid"),
    ;

    private final String CODE;
    private final String MESSAGE;
    private static volatile MultiLangManager multiLangManager;

    @JsonValue
    public String getCode() {
        return this.CODE;
    }

    public String getMessage() {
        return this.MESSAGE;
    }

    /**
     * RequestContextHolder là bean request scoped nên chỉ đc call hàm này trong request scope
     * Nếu call trong các hàm ko cùng thread của request sẽ lỗi ví dụ như call trong @Async
     */
    public String getMessageLang() {
        try {
            MultiLangManager multiLangManager = getMultiLangManager();
            // Thiếu key trong bundle -> trả về message mặc định cứng của enum thay vì tên key thô.
            return multiLangManager.getMessageOrDefault(
                    this.name(), RequestContextHolder.getContext().getLanguage(), this.getMessage());
        } catch (Exception e) {
            log.error("Error get message lang: {}", e.getMessage());
            return this.getMessage();
        }
    }

    public static AppCode initFrom(String name) {
        try {
            for (AppCode instance : AppCode.values()) {
                if (instance.name().equals(name)) {
                    return instance;
                }
            }
        } catch (Exception ignored) {
        }
        return AppCode.UNKNOWN;
    }

    @JsonCreator
    public static AppCode initFromCode(String code) {
        try {
            for (AppCode instance : AppCode.values()) {
                if (instance.CODE.equals(code)) {
                    return instance;
                }
            }
        } catch (Exception ignored) {
        }
        return AppCode.UNKNOWN;
    }

    public boolean matches(String code) {
        try {
            return this.CODE.equals(code);
        } catch (Exception e) {
            return false;
        }
    }

    public <T> Response<T> getResponse() {
        return Response.<T>builder().code(this.CODE).message(this.getMessageLang()).build();
    }

    public <T> Response<T> getResponse(T data) {
        return Response.<T>builder().code(this.CODE).message(this.getMessageLang()).data(data).build();
    }

    public <T> Response<T> getResponse(T data, Object metadata) {
        return Response.<T>builder().code(this.CODE).message(this.getMessageLang()).data(data).metadata(metadata).build();
    }

    public <T> Response<T> getResponse(T data, Object metadata, String message) {
        return Response.<T>builder().code(this.CODE).message(message).data(data).metadata(metadata).build();
    }

    public HttpStatus toHttpStatus() {
        return switch (this) {
            case CODE_400, CODE_402 -> HttpStatus.BAD_REQUEST;
            case CODE_401, TOKEN_IS_NOT_REFRESH, TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case CODE_403 -> HttpStatus.FORBIDDEN;
            case CODE_404 -> HttpStatus.NOT_FOUND;
            case CODE_429 -> HttpStatus.TOO_MANY_REQUESTS;
            case CODE_500 -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.OK;
        };
    }

    AppCode(String code, String message) {
        this.CODE = code;
        this.MESSAGE = message;
    }

    private static MultiLangManager getMultiLangManager() {
        if (multiLangManager == null) {
            synchronized (AppCode.class) {
                if (multiLangManager == null) {
                    multiLangManager = BeanUtil.getBean(MultiLangManager.class);
                }
            }
        }

        return multiLangManager;
    }
}
