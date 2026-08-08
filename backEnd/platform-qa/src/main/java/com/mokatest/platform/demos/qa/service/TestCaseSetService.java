package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.TestCaseSet;

import java.util.List;

/**
 * 测试集 Service
 */
public interface TestCaseSetService extends IService<TestCaseSet> {

    SaResult saveOrUpdateSet(TestCaseSet set);

    SaResult deleteSet(Integer id);

    SaResult listByProject(Integer projectId);

    SaResult listOptions(Integer projectId);

    SaResult listByCaseId(Integer caseId);

    SaResult bindSets(Integer caseId, List<Integer> setIds);

    List<Integer> getCaseIdsBySetId(Integer setId);
}
