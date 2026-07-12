package com.ringme.base.exception;

import com.ringme.base.enums.AppCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BusinessLogicException extends RuntimeException {
    private AppCode code;
    private boolean isTraceStackFull = false;
    private boolean isLog = true;

    public BusinessLogicException(AppCode code) {
        super(code.getMessage());
        this.code = code;
    }

    public BusinessLogicException(AppCode code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessLogicException(AppCode code, String message, boolean isTraceStackFull, boolean isLog) {
        super(message);
        this.code = code;
        this.isTraceStackFull = isTraceStackFull;
        this.isLog = isLog;
    }
}
