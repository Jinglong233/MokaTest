package com.mokatest.platform.demos.exception;

public class InvalidConditionException extends RuntimeException {
    public InvalidConditionException(String message) {
        super(message);
    }
    
    public InvalidConditionException(String message, Throwable cause) {
        super(message, cause);
    }
}