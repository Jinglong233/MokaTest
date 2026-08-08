package com.mokatest.platform.demos.exception;

/**
 * @Author JingLong
 * @Description 断言异常
 * @Date 2025/8/26 16:20
 **/
public class AssertException extends RuntimeException {
    public AssertException(String message) {
        super(message);
    }

    public AssertException(String message, Throwable cause) {
        super(message, cause);
    }
}