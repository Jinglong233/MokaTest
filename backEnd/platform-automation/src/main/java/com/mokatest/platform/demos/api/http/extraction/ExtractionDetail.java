package com.mokatest.platform.demos.api.http.extraction;

import com.mokatest.platform.demos.api.domain.apiEnum.ExtractType;
import com.mokatest.platform.demos.api.domain.apiEnum.RuleSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据提取结果详情
 *
 * 功能说明：封装单条提取规则的执行结果，包含提取值和来源信息，
 * 用于在调试结果中清晰展示每条提取规则的类型、表达式、变量名、提取值和来源
 *
 * @author JingLong
 * @since 2026-05-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionDetail {

    /**
     * 变量名，提取成功后存入变量池的 key
     */
    private String variableName;

    /**
     * 提取到的值
     */
    private Object value;

    /**
     * 提取类型
     */
    private ExtractType type;

    /**
     * 提取表达式
     */
    private String expression;

    /**
     * 规则来源：标识该提取规则来自 GLOBAL/ENVIRONMENT/SCENE/API
     */
    private RuleSource source;

    /**
     * 提取是否成功
     */
    private boolean success;

    /**
     * 提取失败时的错误信息
     */
    private String errorMessage;
}
