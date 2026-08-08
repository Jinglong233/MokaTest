package com.mokatest.platform.demos.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理
 *
 * 统一处理业务异常、登录异常、权限异常等，向前端返回标准化的 SaResult。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    //使用全局异常
    @ExceptionHandler(RuntimeException.class)
    public SaResult handleRuntimeException(RuntimeException e) {
        log.error("服务器异常", e);
        return SaResult.error().setMsg(e.getMessage() == null ? "服务器异常" : e.getMessage());
    }

    //使用自定义的异常
    @ExceptionHandler(BusinessException.class)
    public SaResult CustomException(BusinessException e) {
        return SaResult.error(e.getMessage());
    }


    // 全局未登录异常处理
    @ExceptionHandler(NotLoginException.class)
    public SaResult handlerException(NotLoginException e) {
        return SaResult.code(e.getCode()).setMsg(e.getMessage());
    }

    /**
     * 全局权限不足异常处理
     *
     * 当 Controller 方法上的 @SaCheckPermission 校验失败时，SaToken 会抛出 NotPermissionException，
     * 此处统一捕获并返回 403 提示，避免暴露内部权限编码。
     */
    @ExceptionHandler(NotPermissionException.class)
    public SaResult handleNotPermissionException(NotPermissionException e) {
        log.warn("权限校验失败: {}", e.getMessage());
        return SaResult.code(403).setMsg("无权执行该操作");
    }

    /**
     * 文件上传大小超过限制异常处理
     *
     * Spring Boot 在 Servlet 层面（MultipartResolver）就会校验文件大小，
     * 超限时抛出 MaxUploadSizeExceededException，不会到达 Controller。
     * 此处统一返回中文友好提示。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public SaResult handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件上传大小超过限制: {}", e.getMessage());
        long maxSize = e.getMaxUploadSize();
        String sizeHint = maxSize > 0 ? formatFileSize(maxSize) : "限制";
        return SaResult.error("上传文件大小超过" + sizeHint);
    }

    /**
     * 格式化字节数为可读字符串
     */
    private String formatFileSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return (bytes / (1024 * 1024)) + "MB";
        } else if (bytes >= 1024) {
            return (bytes / 1024) + "KB";
        }
        return bytes + "B";
    }
}