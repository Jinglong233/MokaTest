package com.mokatest.platform.demos.api.http.extraction;

import com.mokatest.platform.demos.api.domain.apiEnum.RuleSource;
import com.mokatest.platform.demos.api.domain.requestModel.ApiExtraction;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;

import java.util.List;
import java.util.Map;

/**
 * 数据提取执行器接口
 *
 * 功能说明：定义从 HTTP 响应中提取数据的标准接口，实现类根据 ApiExtraction 配置
 * 从响应的不同部分（Body、Header、Cookie、状态码）提取数据到变量池
 *
 * 设计目的：
 *   - 解耦提取逻辑与请求执行逻辑，便于扩展新的提取方式
 *   - 支持多种提取策略（JSONPath、正则、Header、Cookie、状态码）
 *   - 提取结果统一返回为 Map，便于后续变量替换使用
 *
 * 执行时机：在 ApiRequestService 中，HTTP 请求执行完成后调用
 *
 * 使用示例：
 *   List<ApiExtraction> extractions = apiRequest.getAssociationExtraction();
 *   if (extractions != null && !extractions.isEmpty()) {
 *       Map<String, Object> variables = extractionExecutor.execute(extractions, httpResponse);
 *       // variables: {token: "abc123", userId: "456"}
 *   }
 *
 * @author JingLong
 * @see DefaultExtractionExecutor
 * @see ApiExtraction
 * @since 2026-05-26
 */
public interface ExtractionExecutor {

    /**
     * 执行数据提取
     *
     * 执行流程：
     *   - 遍历 extractions 列表中的每个提取规则
     *   - 根据 ApiExtraction 选择对应的提取策略
     *   - 从 TestHttpResponse 中提取数据
     *   - 提取成功：以 variableName 为 key，提取值为 value 存入结果 Map
     *   - 提取失败：如果配置了 defaultValue，使用默认值；否则跳过该规则
     *
     * @param extractions 提取配置列表，每个元素定义一条提取规则
     * @param response    HTTP 响应结果对象，包含响应体、响应头、Cookie、状态码等
     * @return 变量名 -> 提取值的映射表；如果 extractions 为空则返回空 Map
     */
    Map<String, Object> execute(List<ApiExtraction> extractions, TestHttpResponse response);

    /**
     * 执行数据提取（带来源信息的详细版本）
     *
     * 与 {@link #execute} 的区别：返回每条规则的详细执行结果，包含来源信息，
     * 便于在调试结果中清晰展示提取规则的配置来源
     *
     * @param extractions 提取配置列表
     * @param response    HTTP 响应结果
     * @param source      提取规则的统一来源标识
     * @return 提取详情列表，包含每条规则的变量名、提取值、来源等信息
     */
    List<ExtractionDetail> executeWithDetails(List<ApiExtraction> extractions, TestHttpResponse response, RuleSource source);
}
