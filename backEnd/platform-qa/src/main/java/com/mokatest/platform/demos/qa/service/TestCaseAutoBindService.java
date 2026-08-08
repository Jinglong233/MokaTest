package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.TestCaseAutoBind;

/**
 * 用例与自动化绑定关系 Service
 */
public interface TestCaseAutoBindService extends IService<TestCaseAutoBind> {

    SaResult bind(Integer testCaseId, String autoType, Integer autoId, String bindRemark);

    SaResult unbind(Integer bindId);

    SaResult listByCaseId(Integer caseId);

    SaResult listAutoOptions(String autoType, Integer projectId);
}
