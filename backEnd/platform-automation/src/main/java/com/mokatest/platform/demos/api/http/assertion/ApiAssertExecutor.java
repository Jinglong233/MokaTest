package com.mokatest.platform.demos.api.http.assertion;

import com.mokatest.platform.demos.api.domain.requestModel.AssertParameter;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;
import com.mokatest.platform.demos.result.AssertResult;

import java.util.List;

/**
 * API 断言执行器接口
 *
 * 功能说明：对 HTTP 响应结果执行断言验证，判断响应是否符合预期
 *
 * 断言目标类型：
 *   - ApiAssertType#BODY - 响应体（JSONPath 提取后比较）
 *   - ApiAssertType#HEADER - 响应头（按名称获取后比较）
 *   - ApiAssertType#STATUS_CODE - HTTP 状态码
 *   - ApiAssertType#RESPONSE_TIME - 响应耗时（毫秒）
 *   - ApiAssertType#CUSTOM - 自定义（正则匹配完整响应体）
 *
 * @author JingLong
 * @see DefaultApiAssertExecutor
 * @since 2026-05-26
 */
public interface ApiAssertExecutor {

    /**
     * 执行断言验证
     *
     * @param assertions 断言规则列表，可为 null 或空
     * @param response   HTTP 响应结果
     * @return 断言结果列表，每条规则对应一个 AssertResult
     */
    List<AssertResult> execute(List<AssertParameter> assertions, TestHttpResponse response);
}
