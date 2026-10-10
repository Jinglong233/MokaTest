package com.mokatest.platform.demos.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 知识库引用明细（检索命中时记录，完成轮次时落库供前端「依据《XX 文档》」展示）。
 *
 * 从旧 SkillRequest.Citation 迁出，便于删除 SkillRequest 框架。
 */
@Data
@AllArgsConstructor
public class Citation {
    private Long docId;
    private String title;
    /** 命中片段（截断保存） */
    private String snippet;
}
