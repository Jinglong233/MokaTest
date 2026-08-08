package com.mokatest.platform.demos.exception;

/**
 * @Author JingLong
 * @Description 参数解析异常
 * @Date 2025/7/25 16:18
 **/
public class ParameterParseException extends RuntimeException {
    public ParameterParseException(String message) {
        super(message);
    }

    public ParameterParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
