package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.qa.domain.TestPlan;
import com.mokatest.platform.demos.qa.domain.TestPlanReport;
import com.mokatest.platform.demos.qa.mapper.TestPlanMapper;
import com.mokatest.platform.demos.qa.mapper.TestPlanReportMapper;
import com.mokatest.platform.demos.qa.service.TestPlanReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestPlanReportServiceImpl extends ServiceImpl<TestPlanReportMapper, TestPlanReport> implements TestPlanReportService {

    private final TestPlanMapper testPlanMapper;
    private final com.mokatest.platform.demos.mapper.UserMapper userMapper;

    @Override
    public SaResult listByProject(Integer projectId, String keyword, Integer page, Integer pageSize) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        QueryWrapper<TestPlanReport> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("report_name", keyword);
        }
        wrapper.orderByDesc("create_time");
        Page<TestPlanReport> result = baseMapper.selectPage(new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10), wrapper);
        List<TestPlanReport> records = result.getRecords();
        if (records.isEmpty()) {
            return SaResult.ok().setData(result);
        }

        // 补充计划名称/创建人；通过率/执行结果读生成时的快照列
        Set<Integer> planIds = records.stream().map(TestPlanReport::getPlanId).collect(Collectors.toSet());
        Map<Integer, TestPlan> planMap = new HashMap<>();
        for (TestPlan p : testPlanMapper.selectBatchIds(planIds)) {
            planMap.put(p.getId(), p);
        }
        Set<Integer> userIds = records.stream().map(TestPlanReport::getCreateUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> userNameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (com.mokatest.platform.demos.domain.ui.User u : userMapper.selectBatchIds(userIds)) {
                userNameMap.put(u.getId().intValue(), u.getUsername());
            }
        }

        Page<Map<String, Object>> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<Map<String, Object>> voList = records.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("reportName", r.getReportName());
            m.put("reportType", r.getReportType());
            m.put("planId", r.getPlanId());
            m.put("planName", planMap.get(r.getPlanId()) != null ? planMap.get(r.getPlanId()).getPlanName() : "-");
            m.put("triggerType", r.getTriggerType());
            m.put("summary", r.getSummary());
            m.put("createUserName", userNameMap.get(r.getCreateUserId()));
            m.put("createTime", r.getCreateTime());
            m.put("passRate", r.getPassRate());
            m.put("execResult", r.getExecResult());
            return m;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return SaResult.ok().setData(voPage);
    }

    @Override
    public SaResult createReport(TestPlanReport report) {
        baseMapper.insert(report);
        return SaResult.ok("生成成功").setData(report);
    }

    @Override
    public SaResult updateSummary(Integer id, String summary) {
        TestPlanReport report = getById(id);
        if (report == null) {
            return SaResult.error("报告不存在");
        }
        report.setSummary(summary);
        report.setUpdateTime(new Date());
        updateById(report);
        return SaResult.ok("保存成功");
    }

    @Override
    public SaResult deleteReport(Integer id) {
        TestPlanReport report = getById(id);
        if (report == null) {
            return SaResult.error("报告不存在");
        }
        removeById(id);
        return SaResult.ok("删除成功");
    }
}
