package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.TestCaseVersion;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseVersionMapper;
import com.mokatest.platform.demos.qa.service.TestCaseOperationLogService;
import com.mokatest.platform.demos.qa.service.TestCaseVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 用例版本快照 Service 实现
 */
@Service
@RequiredArgsConstructor
public class TestCaseVersionServiceImpl extends ServiceImpl<TestCaseVersionMapper, TestCaseVersion> implements TestCaseVersionService {

    private final TestCaseMapper testCaseMapper;
    private final TestCaseOperationLogService testCaseOperationLogService;

    @Override
    public SaResult listByCaseId(Integer caseId) {
        if (caseId == null) {
            return SaResult.error("缺少用例ID");
        }
        QueryWrapper<TestCaseVersion> wrapper = new QueryWrapper<>();
        wrapper.eq("test_case_id", caseId).orderByDesc("version_no");
        List<TestCaseVersion> list = baseMapper.selectList(wrapper);
        return SaResult.ok().setData(list);
    }

    @Override
    public SaResult getDetail(Integer id) {
        if (id == null) {
            return SaResult.error("缺少ID");
        }
        TestCaseVersion version = getById(id);
        if (version == null) {
            return SaResult.error("版本不存在");
        }
        return SaResult.ok().setData(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult rollback(Integer versionId) {
        if (versionId == null) {
            return SaResult.error("缺少版本ID");
        }
        TestCaseVersion version = getById(versionId);
        if (version == null) {
            return SaResult.error("版本不存在");
        }
        TestCase testCase = testCaseMapper.selectById(version.getTestCaseId());
        if (testCase == null) {
            return SaResult.error("用例不存在");
        }
        TestCase snapshotCase;
        try {
            snapshotCase = JSON.parseObject(version.getSnapshot(), TestCase.class);
        } catch (Exception e) {
            return SaResult.error("版本快照解析失败");
        }
        if (snapshotCase == null) {
            return SaResult.error("版本快照为空");
        }
        // 把快照内容写回用例（保留id/归属/审计字段，状态改为 DRAFT 需重审）
        testCase.setCaseName(snapshotCase.getCaseName());
        testCase.setPreCondition(snapshotCase.getPreCondition());
        testCase.setTestSteps(snapshotCase.getTestSteps());
        testCase.setCaseType(snapshotCase.getCaseType());
        testCase.setPriority(snapshotCase.getPriority());
        testCase.setModuleId(snapshotCase.getModuleId());
        testCase.setRequirementId(snapshotCase.getRequirementId());
        // 多需求字段：旧快照（字段加入前生成的）没有 requirement_ids，回退用 requirement_id 兜底，
        // 避免 requirement_id 回滚了但 requirement_ids 残留新值的不一致
        if (snapshotCase.getRequirementIds() != null) {
            testCase.setRequirementIds(snapshotCase.getRequirementIds());
        } else {
            testCase.setRequirementIds(snapshotCase.getRequirementId() != null
                    ? String.valueOf(snapshotCase.getRequirementId()) : "");
        }
        testCase.setTags(snapshotCase.getTags());
        testCase.setExpectDuration(snapshotCase.getExpectDuration());
        testCase.setStatus("DRAFT");
        testCase.setUpdateUserId(StpUtil.getLoginIdAsInt());
        testCase.setUpdateTime(new Date());
        boolean success = testCaseMapper.updateById(testCase) > 0;
        if (!success) {
            return SaResult.error("回滚失败");
        }
        // requirement_id 为 null 时 updateById 不会写库，显式同步一次（含置空场景）
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TestCase> reqSync =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        reqSync.eq("id", testCase.getId()).set("requirement_id", testCase.getRequirementId());
        testCaseMapper.update(null, reqSync);
        testCaseOperationLogService.log(testCase.getId(), "VERSION", "versionNo",
                null, String.valueOf(version.getVersionNo()),
                "回滚到版本 V" + version.getVersionNo() + "，用例状态重置为草稿，需重新评审");
        return SaResult.ok("已回滚到版本 V" + version.getVersionNo());
    }

    @Override
    public void snapshot(TestCase oldCase, String changeNote) {
        if (oldCase == null || oldCase.getId() == null) {
            return;
        }
        QueryWrapper<TestCaseVersion> wrapper = new QueryWrapper<>();
        wrapper.eq("test_case_id", oldCase.getId()).orderByDesc("version_no").last("limit 1");
        TestCaseVersion latest = baseMapper.selectOne(wrapper);
        int nextVersionNo = latest != null && latest.getVersionNo() != null ? latest.getVersionNo() + 1 : 1;

        TestCaseVersion version = new TestCaseVersion();
        version.setTestCaseId(oldCase.getId());
        version.setVersionNo(nextVersionNo);
        version.setSnapshot(JSON.toJSONString(oldCase));
        version.setChangeNote(changeNote);
        Integer loginId = null;
        try {
            loginId = StpUtil.getLoginIdAsInt();
        } catch (Exception ignored) {
            // 无登录上下文时留空
        }
        version.setCreateUserId(loginId);
        version.setCreateTime(new Date());
        save(version);
    }
}
