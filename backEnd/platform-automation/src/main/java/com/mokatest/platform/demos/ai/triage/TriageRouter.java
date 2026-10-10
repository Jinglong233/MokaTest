package com.mokatest.platform.demos.ai.triage;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 失败分诊路由（条件工作流前置 AI Agent）：把步骤失败归类为 DATA / ENV / PRODUCT_BUG。
 *
 * 定位类失败（元素找不到）由 AiLocatorHealService 快速通道处理，不进入分诊。
 */
public interface TriageRouter {

    String SYSTEM = """
            你是自动化测试失败分诊专家。根据步骤信息与错误证据，把失败根因归类为：
            - DATA：数据链路问题（变量缺失/提取失败/参数值错误/断言数据不符/前置数据没造好）
            - ENV：环境故障（服务不可用/网络不通/环境配置错误/连接拒绝/超时且无定位特征）
            - PRODUCT_BUG：疑似产品缺陷（页面行为、接口返回与预期不符，数据和环境都正常）
            只回复一个类别词，不要解释。""";

    @Agent(name = "triageRouter", description = "判断自动化步骤失败的根因类别", outputKey = "category")
    @SystemMessage(SYSTEM)
    @UserMessage("""
            请判断以下自动化测试步骤失败的根因类别，只回复一个类别词（DATA / ENV / PRODUCT_BUG）：

            {{input}}
            """)
    String route(@V("input") String input);
}
