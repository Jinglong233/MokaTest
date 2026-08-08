package com.mokatest.platform.demos.operationlog.annotation;

import com.mokatest.platform.demos.operationlog.enums.OperateType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 标注在 Controller 方法上，自动记录操作日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 所属模块
     */
    String module();

    /**
     * 操作类型
     */
    OperateType type();

    /**
     * 对象类型（如 requirement、bug、testCase）
     */
    String targetType();

    /**
     * 对象ID表达式（SpEL，如 #id、#req.id）
     */
    String targetId() default "";

    /**
     * 对象名称表达式（SpEL）
     */
    String targetName() default "";

    /**
     * 操作描述模板（SpEL）
     */
    String description() default "";

    /**
     * 是否记录请求参数
     */
    boolean recordParams() default true;

    /**
     * 是否记录响应结果
     */
    boolean recordResponse() default false;

    /**
     * 敏感字段（逗号分隔，如 password,token）
     */
    String sensitiveFields() default "";

    /**
     * 需要对比字段变更的实体类（如 Bug.class）
     * 配置后 UPDATE 操作会自动查询旧数据并对比字段差异
     */
    Class<?> compareClass() default Void.class;

    /**
     * 额外忽略的字段（逗号分隔，如 createTime,updateTime）
     */
    String ignoreFields() default "";
}
