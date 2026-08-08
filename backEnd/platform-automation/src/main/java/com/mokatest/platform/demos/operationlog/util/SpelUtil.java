package com.mokatest.platform.demos.operationlog.util;

import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * SpEL 表达式解析工具
 */
public class SpelUtil {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /**
     * 解析 SpEL 表达式（不支持 Bean 引用）
     */
    public static String parse(String expression, Method method, Object[] args) {
        return parse(expression, method, args, null);
    }

    /**
     * 解析 SpEL 表达式
     *
     * @param expression SpEL 表达式，支持 @beanName 引用 Spring Bean（需传入 applicationContext）
     * @param method     目标方法
     * @param args       方法参数
     * @param applicationContext Spring 上下文（可为 null，null 时不支持 @bean 引用）
     * @return 解析结果字符串，解析失败返回原表达式
     */
    public static String parse(String expression, Method method, Object[] args, ApplicationContext applicationContext) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        try {
            StandardEvaluationContext context = new MethodBasedEvaluationContext(
                    null, method, args, PARAMETER_NAME_DISCOVERER);
            if (applicationContext != null) {
                context.setBeanResolver(new BeanFactoryResolver(applicationContext));
            }
            Object value = PARSER.parseExpression(expression).getValue(context);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            // 解析失败返回原表达式作为兜底
            return expression;
        }
    }
}
