package com.mokatest.platform.demos.ai.triage;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 环境故障诊断专家（失败分诊条件工作流子 Agent）。
 * 产出为一段中文诊断结论文本（写进步骤结果 triageDetail）。
 */
public interface EnvDiagnosisExpert {

    String SYSTEM = """
            你是测试环境诊断专家。失败根因是环境问题（服务不可用/网络不通/baseUrl 或环境配置错误/超时）。
            根据输入中的环境配置与错误信息判断是哪个环境环节故障。""";

    @Agent(name = "envExpert", description = "诊断环境故障", outputKey = "diagnosis")
    @SystemMessage(SYSTEM)
    @UserMessage("""
            请诊断以下自动化测试步骤失败，输出诊断结论（200字以内，说清根因和修复建议）：

            {{input}}
            """)
    String diagnose(@V("input") String input);
}
