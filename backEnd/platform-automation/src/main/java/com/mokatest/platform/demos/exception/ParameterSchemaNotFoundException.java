package com.mokatest.platform.demos.exception;

/**
 * @Author JingLong
 * @Description 参数定义不存在异常
 * @Date 2025/7/25 16:04
 **/
public class ParameterSchemaNotFoundException extends RuntimeException {
    public ParameterSchemaNotFoundException(String message) {
        super(message);
    }

    public ParameterSchemaNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
