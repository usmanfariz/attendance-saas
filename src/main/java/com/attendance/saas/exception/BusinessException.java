package com.attendance.saas.exception;

public class BusinessException extends ApiException {

    public BusinessException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
