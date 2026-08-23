package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.Bug;

import java.util.List;

/**
 * BUG池 Service
 */
public interface BugService extends IService<Bug> {

    // requirementScopeId：需求合并口径（直接挂需求 ∪ 需求下用例关联），与 requirementId 精确筛选互斥，传了它忽略 requirementId
    SaResult listByProject(Integer projectId, String keyword, String status, String severity, String priority, Integer requirementId, Integer testCaseId, Integer moduleId, String environment, String reproduceRate, String closeReason, Integer requirementScopeId, Integer page, Integer pageSize);

    SaResult saveOrUpdateBug(Bug bug);

    SaResult deleteBug(Integer id);

    SaResult batchDeleteBug(List<Integer> ids);

    SaResult getDetail(Integer id);

    SaResult transitionStatus(Integer bugId, String targetStatus);

    /** BUG 统计（全项目口径，供顶部统计卡片） */
    SaResult stats(Integer projectId);

    /**
     * 从 BUG 生成回归用例（仅 FIXED/VERIFIED/CLOSED 状态可沉淀）
     *
     * @param folderId 目标用例文件夹，null 时归入根目录(0)
     * @return 新用例ID
     */
    SaResult generateRegressionCase(Integer bugId, Integer folderId);

    /** 关联/解绑用例（testCaseId 为 null 表示解绑） */
    SaResult bindTestCase(Integer bugId, Integer testCaseId);

    /** 批量关联用例 */
    SaResult bindCases(Integer bugId, List<Integer> testCaseIds);

    /** 解绑单个用例 */
    SaResult unbindCase(Integer bugId, Integer testCaseId);

    /** 关联/解绑计划用例（planCaseId 为 null 表示解绑） */
    SaResult bindPlanCase(Integer bugId, Integer planCaseId);

    /** 查询测试计划执行中关联该用例的 BUG（经 test_plan_case 的 plan_case_id 维度） */
    SaResult listPlanCaseBugs(Integer testCaseId);

    /** 查询 BUG 关联的用例列表 */
    SaResult listLinkedCases(Integer bugId);
}
