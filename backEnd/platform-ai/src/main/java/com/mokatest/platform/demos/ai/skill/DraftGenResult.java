package com.mokatest.platform.demos.ai.skill;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成类 Skill 的结构化输出：草稿列表 + 需求不确定点
 *
 * 输出契约（代码写死，不开放项目级覆盖）：
 * {"test_cases": [...], "uncertainties": [...]}
 * uncertainties 让模型主动暴露需求缺陷（缺数值/缺枚举/缺边界），
 * 前端突出展示并引导用户补充说明后重新生成。
 */
@Data
public class DraftGenResult<T> {

    /** 解析出的草稿列表 */
    private List<T> drafts = new ArrayList<>();

    /** 模型识别到的需求不明确点（无则为空数组） */
    private List<String> uncertainties = new ArrayList<>();
}
