package com.johnsonfitness.qq.exception;

import com.johnsonfitness.keycloak.common.exception.BaseKeycloakException;
import com.johnsonfitness.keycloak.common.interfaces.ErrorCode;
import jakarta.ws.rs.core.Response;

public class QQException extends BaseKeycloakException {

    public QQException(ErrorCode errorCode, Response.Status httpStatus) {
        super(errorCode, httpStatus);
    }

    public QQException(ErrorCode errorCode, Response.Status httpStatus, String details) {
        super(errorCode, httpStatus, details);
    }

    public QQException(ErrorCode errorCode, Response.Status httpStatus, Throwable cause) {
        super(errorCode, httpStatus, cause);
    }

}
