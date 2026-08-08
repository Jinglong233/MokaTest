package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.TestCase;

import java.util.List;

/**
 * 文字用例 Service
 */
public interface TestCaseService extends IService<TestCase> {

    SaResult listByProject(Integer projectId, Integer moduleId, Integer setId, Integer requirementId, String keyword, String lastResult, Integer page, Integer pageSize, Integer excludePlanId);

    SaResult saveOrUpdateCase(TestCase testCase);

    SaResult deleteCase(Integer id);

    SaResult batchDeleteCase(List<Integer> ids);

    SaResult getDetail(Integer id);

    SaResult listIdsByProject(Integer projectId, Integer moduleId, Integer setId, Integer requirementId, String keyword, String lastResult, Integer excludePlanId);

    void exportExcel(Integer projectId, Integer moduleId, Integer setId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException;

    SaResult transitionStatus(Integer testCaseId, String targetStatus);

    /** 用例统计（全项目口径，供顶部统计卡片） */
    SaResult stats(Integer projectId);
}
