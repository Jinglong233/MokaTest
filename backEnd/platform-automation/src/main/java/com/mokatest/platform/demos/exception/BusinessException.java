package com.mokatest.platform.demos.exception;

import lombok.Data;

//自定义异常类
@Data
public class BusinessException extends RuntimeException {

    private String msg;

    public BusinessException() {

    }

    public BusinessException(String message) {
        super(message);
        this.msg = message;
    }


}