package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.TestCaseVersion;

/**
 * 用例版本快照 Service
 */
public interface TestCaseVersionService extends IService<TestCaseVersion> {

    /**
     * 用例的版本列表（按版本号倒序）
     */
    SaResult listByCaseId(Integer caseId);

    /**
     * 版本快照详情
     */
    SaResult getDetail(Integer id);

    /**
     * 回滚到指定版本：快照内容写回用例（状态改为 DRAFT，需重审），并写操作日志
     */
    SaResult rollback(Integer versionId);

    /**
     * 为指定用例生成一条快照（version_no 自增）
     */
    void snapshot(TestCase oldCase, String changeNote);
}
