package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.TestCase;

import java.util.List;

/**
 * 文字用例 Service
 */
public interface TestCaseService extends IService<TestCase> {

    SaResult listByProject(Integer projectId, Integer moduleId, Integer setId, Integer requirementId, String keyword, String lastResult, Integer page, Integer pageSize, Integer excludePlanId, String status);

    SaResult saveOrUpdateCase(TestCase testCase);

    SaResult deleteCase(Integer id);

    SaResult batchDeleteCase(List<Integer> ids);

    SaResult batchMoveModule(List<Integer> ids, Integer moduleId);

    /**
     * 批量排序：按传入 id 顺序重排 sort 值（0..n），用于脑图兄弟节点顺序持久化
     */
    SaResult sortCases(List<Integer> ids);

    SaResult getDetail(Integer id);

    SaResult listIdsByProject(Integer projectId, Integer moduleId, Integer setId, Integer requirementId, String keyword, String lastResult, Integer excludePlanId);

    void exportExcel(Integer projectId, Integer moduleId, Integer setId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException;

    SaResult transitionStatus(Integer testCaseId, String targetStatus, String comment);

    /** 用例统计（全项目口径，供顶部统计卡片） */
    SaResult stats(Integer projectId);
}
