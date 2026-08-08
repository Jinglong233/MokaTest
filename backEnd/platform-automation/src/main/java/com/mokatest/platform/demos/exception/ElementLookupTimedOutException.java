package com.mokatest.platform.demos.exception;

/**
 * @Author JingLong
 * @Description 元素查找超时异常
 * @Date 2025/10/16 20:11
 **/
public class ElementLookupTimedOutException extends RuntimeException {
    public ElementLookupTimedOutException(String message) {
        super(message);
    }

    public ElementLookupTimedOutException(String message, Throwable cause) {
        super(message, cause);
    }
}