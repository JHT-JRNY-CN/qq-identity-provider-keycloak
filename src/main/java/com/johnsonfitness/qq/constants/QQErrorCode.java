package com.johnsonfitness.qq.constants;

import com.johnsonfitness.keycloak.common.interfaces.ErrorCode;

public enum QQErrorCode implements ErrorCode {
    ;

    private final String code;
    private final String message;

    QQErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
