package com.mokatest.platform.demos.exception;

/**
 * @Author JingLong
 * @Description 不支持的步骤类型异常
 * @Date 2025/7/25 14:41
 **/
public class UnsupportedStepTypeException extends RuntimeException{
    public UnsupportedStepTypeException(String message) {
        super(message);
    }

    public UnsupportedStepTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
