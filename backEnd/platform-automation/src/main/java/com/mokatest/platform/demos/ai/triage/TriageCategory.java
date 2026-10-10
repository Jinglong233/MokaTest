package com.mokatest.platform.demos.ai.triage;

/**
 * 失败分诊类别（定位类失败走 AiLocatorHealService 快速通道，不在此列）。
 */
public enum TriageCategory {
    /** 数据问题：变量/提取/参数链路错误 */
    DATA,
    /** 环境故障：环境配置/网络/服务不可用 */
    ENV,
    /** 疑似产品 BUG：应用行为与预期不符 */
    PRODUCT_BUG
}
