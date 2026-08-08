package com.mokatest.platform.demos.exception;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/8/1 20:29
 **/
public class ParamIsEmptyException extends RuntimeException {
    public ParamIsEmptyException(String message) {
        super(message);
    }

    public ParamIsEmptyException(String message, Throwable cause) {
        super(message, cause);
    }
}
