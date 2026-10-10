package com.mokatest.platform.demos.ai.triage;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 数据问题诊断专家（失败分诊条件工作流子 Agent）。
 * 产出为一段中文诊断结论文本（写进步骤结果 triageDetail）。
 */
public interface DataDiagnosisExpert {

    String SYSTEM = """
            你是测试数据诊断专家。失败根因是数据链路问题（变量缺失/提取失败/参数值错误/断言数据不符）。
            根据输入中的变量状态、提取结果、接口响应定位是哪一环的数据出错。""";

    @Agent(name = "dataExpert", description = "诊断数据链路问题", outputKey = "diagnosis")
    @SystemMessage(SYSTEM)
    @UserMessage("""
            请诊断以下自动化测试步骤失败，输出诊断结论（200字以内，说清根因和修复建议）：

            {{input}}
            """)
    String diagnose(@V("input") String input);
}
