package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.qa.domain.*;
import com.mokatest.platform.demos.qa.mapper.*;
import com.mokatest.platform.demos.qa.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestPlanServiceImpl extends ServiceImpl<TestPlanMapper, TestPlan> implements TestPlanService {

    private final TestPlanMapper testPlanMapper;
    private final TestPlanCaseMapper testPlanCaseMapper;
    private final TestCaseExecutionMapper testCaseExecutionMapper;
    private final TestCaseMapper testCaseMapper;
    private final BugMapper bugMapper;
    private final com.mokatest.platform.demos.qa.mapper.BugCaseMapper bugCaseMapper;
    private final com.mokatest.platform.demos.mapper.UserMapper userMapper;
    private final com.mokatest.platform.demos.qa.service.BugService bugService;
    private final PlanTestPointMapper planTestPointMapper;
    private final QaModuleMapper qaModuleMapper;
    private final RequirementMapper requirementMapper;
    private final com.mokatest.platform.demos.qa.service.TestPlanReportService testPlanReportService;

    @Override
    public SaResult listByProject(Integer projectId, String keyword, String status, Integer moduleId, Integer page, Integer pageSize) {
        QueryWrapper<TestPlan> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("plan_name", keyword).or().like("plan_code", keyword));
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        if (moduleId != null) {
            wrapper.eq("module_id", moduleId);
        }
        wrapper.orderByDesc("create_time");
        Page<TestPlan> pageParam = new Page<>(page, pageSize);
        Page<TestPlan> result = testPlanMapper.selectPage(pageParam, wrapper);
        List<TestPlan> records = result.getRecords();
        if (records.isEmpty()) {
            return SaResult.ok().setData(result);
        }

        // 聚合：用例数/通过数/执行结果汇总（按计划分组）
        List<Integer> planIds = records.stream().map(TestPlan::getId).collect(Collectors.toList());
        QueryWrapper<TestPlanCase> pcW = new QueryWrapper<>();
        pcW.in("plan_id", planIds);
        List<TestPlanCase> allCases = testPlanCaseMapper.selectList(pcW);
        Map<Integer, List<TestPlanCase>> caseMap = allCases.stream().collect(Collectors.groupingBy(TestPlanCase::getPlanId));

        // 创建人/模块名称
        Set<Integer> userIds = records.stream().map(TestPlan::getCreateUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> userNameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (com.mokatest.platform.demos.domain.ui.User u : userMapper.selectBatchIds(userIds)) {
                userNameMap.put(u.getId().intValue(), u.getUsername());
            }
        }
        Set<Integer> modIds = records.stream().map(TestPlan::getModuleId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> modNameMap = new HashMap<>();
        if (!modIds.isEmpty()) {
            for (QaModule m : qaModuleMapper.selectBatchIds(modIds)) {
                modNameMap.put(m.getId(), m.getModuleName());
            }
        }

        Page<TestPlanListVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<TestPlanListVO> voList = records.stream().map(p -> {
            TestPlanListVO vo = new TestPlanListVO();
            BeanUtils.copyProperties(p, vo);
            vo.setCreateUserName(userNameMap.get(p.getCreateUserId()));
            vo.setModuleName(modNameMap.get(p.getModuleId()));
            List<TestPlanCase> pcs = caseMap.getOrDefault(p.getId(), List.of());
            int total = pcs.size();
            long pass = pcs.stream().filter(c -> "PASS".equals(c.getExecuteResult())).count();
            long fail = pcs.stream().filter(c -> "FAIL".equals(c.getExecuteResult())).count();
            long block = pcs.stream().filter(c -> "BLOCK".equals(c.getExecuteResult())).count();
            long unexec = total - pass - fail - block - pcs.stream().filter(c -> "NA".equals(c.getExecuteResult())).count();
            vo.setCaseCount(total);
            vo.setPassRate(total > 0 ? Math.round(pass * 100f / total) : 0);
            // 执行结果汇总：无用例或全部未执行 → NONE；全部通过 → ALL_PASS；其余 → NOT_PASS
            String execResult;
            if (total == 0 || unexec == total) execResult = "NONE";
            else if (pass == total) execResult = "ALL_PASS";
            else execResult = "NOT_PASS";
            vo.setExecResult(execResult);
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return SaResult.ok().setData(voPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult saveOrUpdatePlan(TestPlan plan, Integer loginUserId) {
        // 记录旧状态，用于「完成时自动生成报告」
        TestPlan oldPlan = plan.getId() != null ? getById(plan.getId()) : null;
        String oldStatus = oldPlan != null ? oldPlan.getStatus() : null;
        if (plan.getId() == null) {
            plan.setCreateUserId(loginUserId);
            plan.setStatus("DRAFT");
            plan.setCreateTime(new Date());
            // 先占临时唯一值，插入拿到自增 id 后改成 TP-{id}
            plan.setPlanCode("TMP-" + System.nanoTime());
        }
        plan.setUpdateTime(new Date());
        boolean success = saveOrUpdate(plan);
        if (!success) {
            return SaResult.error("保存失败");
        }
        if (plan.getPlanCode() != null && plan.getPlanCode().startsWith("TMP-")) {
            plan.setPlanCode("TP-" + plan.getId());
            updateById(plan);
        }
        // 状态流转为已完成时自动生成报告
        if ("COMPLETED".equals(plan.getStatus()) && !"COMPLETED".equals(oldStatus)) {
            generatePlanReport(plan.getId(), "AUTO", loginUserId);
        }
        return SaResult.ok("保存成功").setData(plan);
    }

    /**
     * 生成计划报告：实时计算完整报告数据并作为快照固化到 content（之后报告内容不再随源数据变化）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult generatePlanReport(Integer planId, String triggerType, Integer loginUserId) {
        TestPlan plan = getById(planId);
        if (plan == null) {
            return SaResult.error("计划不存在");
        }
        SaResult reportRes = getPlanReport(planId);
        if (reportRes.getCode() != 200 || reportRes.getData() == null) {
            return SaResult.error("报告数据生成失败");
        }
        PlanReportVO vo = (PlanReportVO) reportRes.getData();
        String contentJson;
        try {
            contentJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(vo);
        } catch (Exception e) {
            return SaResult.error("报告序列化失败");
        }
        // 列表页展示用的快照指标随报告固化
        int passRateSnap = vo.getTotal() > 0 ? Math.round(vo.getPass() * 100f / vo.getTotal()) : 0;
        String execResultSnap;
        if (vo.getTotal() == 0 || vo.getUnexec() == vo.getTotal()) execResultSnap = "NONE";
        else if (vo.getPass() == vo.getTotal()) execResultSnap = "ALL_PASS";
        else execResultSnap = "NOT_PASS";
        TestPlanReport report = new TestPlanReport();
        report.setReportName(plan.getPlanName() + "-报告-" + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
        report.setReportType("PLAN");
        report.setPlanId(plan.getId());
        report.setProjectId(plan.getProjectId());
        report.setTriggerType(triggerType);
        report.setContent(contentJson);
        report.setPassRate(passRateSnap);
        report.setExecResult(execResultSnap);
        report.setCreateUserId(loginUserId);
        report.setCreateTime(new Date());
        return testPlanReportService.createReport(report);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult deletePlan(Integer id) {
        // 删除计划下的用例关联（依附数据，物理清理）
        QueryWrapper<TestPlanCase> wrapper = new QueryWrapper<>();
        wrapper.eq("plan_id", id);
        testPlanCaseMapper.delete(wrapper);
        // 逻辑删除计划
        TestPlan plan = testPlanMapper.selectById(id);
        if (plan == null) {
            return SaResult.ok("删除成功");
        }
        plan.setDeletedAt(new Date());
        boolean success = removeById(plan);
        return success ? SaResult.ok("删除成功") : SaResult.error("删除失败");
    }

    @Override
    public SaResult getPlanDetail(Integer id) {
        TestPlan plan = getById(id);
        if (plan == null) {
            return SaResult.error("计划不存在");
        }
        // 查询计划下的用例
        QueryWrapper<TestPlanCase> wrapper = new QueryWrapper<>();
        wrapper.eq("plan_id", id).orderByAsc("sort", "id");
        List<TestPlanCase> planCases = testPlanCaseMapper.selectList(wrapper);
        // 查询用例详情
        List<Integer> caseIds = planCases.stream().map(TestPlanCase::getTestCaseId).collect(Collectors.toList());
        List<TestCase> cases = caseIds.isEmpty() ? List.of() : testCaseMapper.selectBatchIds(caseIds);
        // 查询所有计划用例关联的BUG（一对多）
        List<Integer> planCaseIds = planCases.stream().map(TestPlanCase::getId).collect(Collectors.toList());
        Map<Integer, List<BugMiniVO>> bugMap = new HashMap<>();
        if (!planCaseIds.isEmpty()) {
            QueryWrapper<Bug> bugWrapper = new QueryWrapper<>();
            bugWrapper.in("plan_case_id", planCaseIds);
            List<Bug> bugs = bugMapper.selectList(bugWrapper);
            // 补充报告人/处理人昵称
            Set<Integer> bugUserIds = new HashSet<>();
            for (Bug b : bugs) {
                if (b.getReporterId() != null) bugUserIds.add(b.getReporterId());
                if (b.getAssigneeId() != null) bugUserIds.add(b.getAssigneeId());
            }
            Map<Integer, String> bugUserNameMap = new HashMap<>();
            if (!bugUserIds.isEmpty()) {
                for (com.mokatest.platform.demos.domain.ui.User u : userMapper.selectBatchIds(bugUserIds)) {
                    bugUserNameMap.put(u.getId().intValue(), u.getUsername());
                }
            }
            final Map<Integer, String> nameMap = bugUserNameMap;
            // 统计每个 BUG 的关联用例数（bug_case 绑定数 + 来源计划用例）
            Map<Integer, Long> bugCaseCount = new HashMap<>();
            if (!bugs.isEmpty()) {
                QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> bcW = new QueryWrapper<>();
                bcW.in("bug_id", bugs.stream().map(Bug::getId).collect(Collectors.toList()));
                bugCaseCount = bugCaseMapper.selectList(bcW).stream()
                        .collect(Collectors.groupingBy(com.mokatest.platform.demos.qa.domain.BugCase::getBugId, Collectors.counting()));
            }
            final Map<Integer, Long> ccMap = bugCaseCount;
            bugMap.putAll(bugs.stream()
                    .collect(Collectors.groupingBy(Bug::getPlanCaseId,
                            Collectors.mapping(b -> new BugMiniVO(b.getId(), b.getBugCode(), b.getTitle(), b.getStatus(),
                                            nameMap.getOrDefault(b.getReporterId(), ""), nameMap.getOrDefault(b.getAssigneeId(), ""),
                                            (int) (ccMap.getOrDefault(b.getId(), 0L) + (b.getPlanCaseId() != null ? 1 : 0)),
                                            b.getCreateTime()),
                                    Collectors.toList()))));
        }
        // 查询测试点名称
        Set<Integer> tpIds = planCases.stream().map(TestPlanCase::getTestPointId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> tpNameMap = new HashMap<>();
        if (!tpIds.isEmpty()) {
            for (PlanTestPoint tp : planTestPointMapper.selectBatchIds(tpIds)) {
                tpNameMap.put(tp.getId(), tp.getName());
            }
        }
        // 查询模块名称
        Set<Integer> modIds = cases.stream().map(TestCase::getModuleId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> modNameMap = new HashMap<>();
        if (!modIds.isEmpty()) {
            for (QaModule m : qaModuleMapper.selectBatchIds(modIds)) {
                modNameMap.put(m.getId(), m.getModuleName());
            }
        }
        // 查询用户名称（创建人+执行人）
        Set<Integer> userIds = new HashSet<>();
        for (TestCase c : cases) { if (c.getCreateUserId() != null) userIds.add(c.getCreateUserId()); }
        for (TestPlanCase p0 : planCases) { if (p0.getExecuteUserId() != null) userIds.add(p0.getExecuteUserId()); }
        Map<Integer, String> userNameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (com.mokatest.platform.demos.domain.ui.User u : userMapper.selectBatchIds(userIds)) {
                userNameMap.put(u.getId().intValue(), u.getUsername());
            }
        }
        // 组装结果
        List<TestPlanCaseVO> voList = planCases.stream().map(pc -> {
            TestPlanCaseVO vo = new TestPlanCaseVO();
            vo.setId(pc.getId());
            vo.setPlanId(pc.getPlanId());
            vo.setTestCaseId(pc.getTestCaseId());
            vo.setSort(pc.getSort());
            vo.setExecuteResult(pc.getExecuteResult());
            vo.setExecuteRemark(pc.getExecuteRemark());
            vo.setStepResults(pc.getStepResults());
            vo.setExecuteUserId(pc.getExecuteUserId());
            vo.setExecuteTime(pc.getExecuteTime());
            vo.setExecuteUserName(userNameMap.get(pc.getExecuteUserId()));
            vo.setTestPointId(pc.getTestPointId());
            vo.setTestPointName(tpNameMap.get(pc.getTestPointId()));
            // 填充用例信息
            TestCase tc = cases.stream().filter(c -> c.getId().equals(pc.getTestCaseId())).findFirst().orElse(null);
            if (tc != null) {
                vo.setCaseCode(tc.getCaseCode());
                vo.setCaseName(tc.getCaseName());
                vo.setCaseType(tc.getCaseType());
                vo.setPriority(tc.getPriority());
                vo.setPreCondition(tc.getPreCondition());
                vo.setTestSteps(tc.getTestSteps());
                vo.setTags(tc.getTags());
                vo.setModuleId(tc.getModuleId());
                vo.setModuleName(modNameMap.get(tc.getModuleId()));
                vo.setCreateUserName(userNameMap.get(tc.getCreateUserId()));
                vo.setCreateTime(tc.getCreateTime());
                vo.setUpdateTime(tc.getUpdateTime());
            }
            // 填充关联BUG列表
            vo.setBugList(bugMap.getOrDefault(pc.getId(), List.of()));
            return vo;
        }).collect(Collectors.toList());
        return SaResult.ok().setData(new PlanDetailVO(plan, voList));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult addCases(Integer planId, List<Integer> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) {
            return SaResult.error("用例ID列表不能为空");
        }
        // 查询已有用例，避免重复添加
        QueryWrapper<TestPlanCase> wrapper = new QueryWrapper<>();
        wrapper.eq("plan_id", planId).in("test_case_id", caseIds);
        List<TestPlanCase> existing = testPlanCaseMapper.selectList(wrapper);
        List<Integer> existingCaseIds = existing.stream().map(TestPlanCase::getTestCaseId).collect(Collectors.toList());
        List<Integer> newCaseIds = caseIds.stream().filter(id -> !existingCaseIds.contains(id)).collect(Collectors.toList());
        if (newCaseIds.isEmpty()) {
            return SaResult.ok("所选用例已全部在计划中");
        }
        for (Integer caseId : newCaseIds) {
            TestPlanCase pc = new TestPlanCase();
            pc.setPlanId(planId);
            pc.setTestCaseId(caseId);
            pc.setExecuteResult("UNEXECUTED");
            pc.setCreateTime(new Date());
            testPlanCaseMapper.insert(pc);
        }
        return SaResult.ok("添加成功").setData(newCaseIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult removeCase(Integer planCaseId) {
        testPlanCaseMapper.deleteById(planCaseId);
        return SaResult.ok("移除成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult executeCase(Integer planCaseId, String result, String remark, String stepResults, Integer executeUserId) {
        TestPlanCase pc = testPlanCaseMapper.selectById(planCaseId);
        if (pc == null) {
            return SaResult.error("计划用例不存在");
        }
        // 更新计划用例执行结果
        pc.setExecuteResult(result);
        pc.setExecuteRemark(remark);
        pc.setStepResults(stepResults);
        pc.setExecuteUserId(executeUserId);
        pc.setExecuteTime(new Date());
        testPlanCaseMapper.updateById(pc);
        // 写入执行历史
        TestCaseExecution execution = new TestCaseExecution();
        execution.setTestCaseId(pc.getTestCaseId());
        execution.setPlanId(pc.getPlanId());
        execution.setResult(result);
        execution.setRemark(remark);
        execution.setExecuteUserId(executeUserId);
        execution.setExecuteTime(new Date());
        // 冗余存储用例名称（防删除）
        TestCase testCase = testCaseMapper.selectById(pc.getTestCaseId());
        if (testCase != null) {
            execution.setTestCaseName(testCase.getCaseName());
            testCase.setLastResult(result);
            testCase.setLastExecuteTime(new Date());
            testCaseMapper.updateById(testCase);
        } else {
            execution.setTestCaseName("用例已删除");
        }
        testCaseExecutionMapper.insert(execution);
        return SaResult.ok("执行结果已记录");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult batchExecute(Integer planId, List<Integer> planCaseIds, String result, Integer executeUserId) {
        if (planCaseIds == null || planCaseIds.isEmpty()) {
            return SaResult.error("未选择用例");
        }
        Date now = new Date();
        for (Integer planCaseId : planCaseIds) {
            TestPlanCase pc = testPlanCaseMapper.selectById(planCaseId);
            if (pc == null) continue;
            pc.setExecuteResult(result);
            pc.setExecuteTime(now);
            pc.setExecuteUserId(executeUserId);
            testPlanCaseMapper.updateById(pc);
            // 写入执行历史
            TestCaseExecution execution = new TestCaseExecution();
            execution.setTestCaseId(pc.getTestCaseId());
            execution.setPlanId(planId);
            execution.setResult(result);
            execution.setExecuteUserId(executeUserId);
            execution.setExecuteTime(now);
            // 冗余存储用例名称（防删除）
            TestCase tc = testCaseMapper.selectById(pc.getTestCaseId());
            if (tc != null) {
                execution.setTestCaseName(tc.getCaseName());
                tc.setLastResult(result);
                tc.setLastExecuteTime(now);
                testCaseMapper.updateById(tc);
            } else {
                execution.setTestCaseName("用例已删除");
            }
            testCaseExecutionMapper.insert(execution);
        }
        return SaResult.ok("批量执行完成");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult generateBugFromFailCase(Integer planCaseId, Bug bug, Integer createUserId) {
        TestPlanCase pc = testPlanCaseMapper.selectById(planCaseId);
        if (pc == null) {
            return SaResult.error("计划用例不存在");
        }
        if (!"FAIL".equals(pc.getExecuteResult())) {
            return SaResult.error("只有执行失败的用例才能提BUG");
        }
        TestCase testCase = testCaseMapper.selectById(pc.getTestCaseId());
        if (testCase == null) {
            return SaResult.error("用例不存在");
        }
        // 自动填充关联字段（用户填写的表单中不会传这些）
        bug.setProjectId(testCase.getProjectId());
        bug.setPlanCaseId(planCaseId);
        bug.setRequirementId(testCase.getRequirementId());
        bug.setReporterId(createUserId);
        bug.setCreateUserId(createUserId);
        bug.setCreateTime(new Date());
        // 默认值兜底
        if (bug.getStatus() == null || bug.getStatus().isEmpty()) {
            bug.setStatus("NEW");
        }
        if (bug.getSeverity() == null || bug.getSeverity().isEmpty()) {
            bug.setSeverity("NORMAL");
        }
        if (bug.getPriority() == null || bug.getPriority().isEmpty()) {
            bug.setPriority("MEDIUM");
        }
        SaResult result = bugService.saveOrUpdateBug(bug);
        if (result.getCode() != 200) {
            return result;
        }
        // 关联 BUG 到当前用例（通过 bug_case 关联表）
        bugService.bindCases(bug.getId(), java.util.Collections.singletonList(testCase.getId()));
        // 回填计划用例的bug_id（记录最近一次）
        pc.setBugId(bug.getId());
        testPlanCaseMapper.updateById(pc);
        // 回填执行记录的bug_id（找该用例在该计划下最近一条FAIL记录）
        QueryWrapper<TestCaseExecution> execWrapper = new QueryWrapper<>();
        execWrapper.eq("test_case_id", pc.getTestCaseId())
                .eq("plan_id", pc.getPlanId())
                .eq("result", "FAIL")
                .orderByDesc("execute_time")
                .last("LIMIT 1");
        TestCaseExecution latestFail = testCaseExecutionMapper.selectOne(execWrapper);
        if (latestFail != null) {
            latestFail.setBugId(bug.getId());
            testCaseExecutionMapper.updateById(latestFail);
        }
        return SaResult.ok("Bug创建成功").setData(bug.getId());
    }

    @Override
    public SaResult getExecutionHistory(Integer testCaseId) {
        if (testCaseId == null) {
            return SaResult.error("缺少用例ID");
        }
        QueryWrapper<TestCaseExecution> wrapper = new QueryWrapper<>();
        wrapper.eq("test_case_id", testCaseId).orderByDesc("execute_time");
        List<TestCaseExecution> list = testCaseExecutionMapper.selectList(wrapper);
        // 补充计划名称
        Set<Integer> planIds = list.stream().map(TestCaseExecution::getPlanId).filter(Objects::nonNull).collect(Collectors.toSet());
        final Map<Integer, String> planMap = new HashMap<>();
        if (!planIds.isEmpty()) {
            List<TestPlan> plans = testPlanMapper.selectBatchIds(planIds);
            planMap.putAll(plans.stream().collect(Collectors.toMap(TestPlan::getId, TestPlan::getPlanName, (a, b) -> a)));
        }
        // 补充执行人名称
        Set<Integer> userIds = list.stream().map(TestCaseExecution::getExecuteUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        final Map<Integer, String> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<com.mokatest.platform.demos.domain.ui.User> users = userMapper.selectBatchIds(userIds);
            for (com.mokatest.platform.demos.domain.ui.User u : users) {
                userMap.put(u.getId().intValue(), u.getUsername());
            }
        }
        List<ExecutionHistoryVO> voList = list.stream().map(e -> {
            ExecutionHistoryVO vo = new ExecutionHistoryVO();
            vo.setId(e.getId());
            vo.setTestCaseId(e.getTestCaseId());
            vo.setTestCaseName(e.getTestCaseName());
            vo.setPlanId(e.getPlanId());
            vo.setPlanName(planMap.getOrDefault(e.getPlanId(), ""));
            vo.setResult(e.getResult());
            vo.setRemark(e.getRemark());
            vo.setExecuteUserId(e.getExecuteUserId());
            vo.setExecuteUserName(userMap.getOrDefault(e.getExecuteUserId(), ""));
            vo.setExecuteTime(e.getExecuteTime());
            return vo;
        }).collect(Collectors.toList());
        return SaResult.ok().setData(voList);
    }

    @lombok.Data
    public static class ExecutionHistoryVO {
        private Integer id;
        private Integer testCaseId;
        private String testCaseName;
        private Integer planId;
        private String planName;
        private String result;
        private String remark;
        private Integer executeUserId;
        private String executeUserName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date executeTime;
    }

    @Override
    public SaResult getPlanReport(Integer planId) {
        TestPlan plan = getById(planId);
        if (plan == null) {
            return SaResult.error("计划不存在");
        }

        // 1. 查询计划下的所有用例关联
        QueryWrapper<TestPlanCase> tpcWrapper = new QueryWrapper<>();
        tpcWrapper.eq("plan_id", planId).orderByAsc("sort", "id");
        List<TestPlanCase> planCases = testPlanCaseMapper.selectList(tpcWrapper);
        List<Integer> caseIds = planCases.stream().map(TestPlanCase::getTestCaseId).collect(Collectors.toList());

        // 2. 查询用例详情
        List<TestCase> cases = caseIds.isEmpty() ? List.of() : testCaseMapper.selectBatchIds(caseIds);
        Map<Integer, TestCase> caseMap = cases.stream().collect(Collectors.toMap(TestCase::getId, c -> c, (a, b) -> a));

        // 3. 查询模块信息
        Set<Integer> moduleIds = cases.stream().map(TestCase::getModuleId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> moduleMap = new HashMap<>();
        if (!moduleIds.isEmpty() && qaModuleMapper != null) {
            List<com.mokatest.platform.demos.qa.domain.QaModule> modules = qaModuleMapper.selectBatchIds(moduleIds);
            moduleMap = modules.stream().collect(Collectors.toMap(
                    com.mokatest.platform.demos.qa.domain.QaModule::getId,
                    com.mokatest.platform.demos.qa.domain.QaModule::getModuleName,
                    (a, b) -> a));
        }

        // 4. 查询所有关联BUG
        List<Integer> planCaseIds = planCases.stream().map(TestPlanCase::getId).collect(Collectors.toList());
        List<Bug> bugs = List.of();
        if (!planCaseIds.isEmpty()) {
            QueryWrapper<Bug> bugWrapper = new QueryWrapper<>();
            bugWrapper.in("plan_case_id", planCaseIds);
            bugs = bugMapper.selectList(bugWrapper);
        }

        // 5. 执行统计
        int total = planCases.size();
        int pass = 0, fail = 0, block = 0, na = 0, unexec = 0;
        for (TestPlanCase pc : planCases) {
            String r = pc.getExecuteResult();
            if ("PASS".equals(r)) pass++;
            else if ("FAIL".equals(r)) fail++;
            else if ("BLOCK".equals(r)) block++;
            else if ("NA".equals(r)) na++;
            else unexec++;
        }

        // 6. BUG统计
        int totalBug = bugs.size();
        int openBug = (int) bugs.stream().filter(b -> !"CLOSED".equals(b.getStatus()) && !"REJECTED".equals(b.getStatus())).count();
        Map<String, Long> bugBySeverity = bugs.stream().collect(Collectors.groupingBy(Bug::getSeverity, Collectors.counting()));
        Map<String, Long> bugByStatus = bugs.stream().collect(Collectors.groupingBy(Bug::getStatus, Collectors.counting()));

        // 7. 失败用例明细
        List<Map<String, Object>> failCaseList = new ArrayList<>();
        for (TestPlanCase pc : planCases) {
            if (!"FAIL".equals(pc.getExecuteResult()) && !"BLOCK".equals(pc.getExecuteResult())) continue;
            TestCase tc = caseMap.get(pc.getTestCaseId());
            if (tc == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("caseCode", tc.getCaseCode());
            m.put("caseName", tc.getCaseName());
            m.put("priority", tc.getPriority());
            m.put("moduleName", moduleMap.getOrDefault(tc.getModuleId(), ""));
            m.put("executeResult", pc.getExecuteResult());
            m.put("executeRemark", pc.getExecuteRemark());
            m.put("executeTime", pc.getExecuteTime());
            // 关联BUG
            List<Map<String, Object>> caseBugs = new ArrayList<>();
            for (Bug b : bugs) {
                if (b.getPlanCaseId() != null && b.getPlanCaseId().equals(pc.getId())) {
                    Map<String, Object> bm = new HashMap<>();
                    bm.put("bugCode", b.getBugCode());
                    bm.put("title", b.getTitle());
                    bm.put("severity", b.getSeverity());
                    caseBugs.add(bm);
                }
            }
            m.put("bugs", caseBugs);
            failCaseList.add(m);
        }

        // 8. 模块统计
        final Map<Integer, String> finalModuleMap = moduleMap;
        Map<Integer, ModuleStat> moduleStatMap = new HashMap<>();
        for (TestPlanCase pc : planCases) {
            TestCase tc = caseMap.get(pc.getTestCaseId());
            if (tc == null || tc.getModuleId() == null) continue;
            Integer mid = tc.getModuleId();
            ModuleStat stat = moduleStatMap.get(mid);
            if (stat == null) {
                stat = new ModuleStat();
                stat.moduleName = finalModuleMap.getOrDefault(mid, "未知模块");
                moduleStatMap.put(mid, stat);
            }
            stat.total++;
            if ("PASS".equals(pc.getExecuteResult())) stat.pass++;
            else if ("FAIL".equals(pc.getExecuteResult())) stat.fail++;
            else if ("BLOCK".equals(pc.getExecuteResult())) stat.block++;
        }
        // 补充模块BUG数
        for (Bug b : bugs) {
            if (b.getModuleId() == null) continue;
            ModuleStat stat = moduleStatMap.get(b.getModuleId());
            if (stat != null) stat.bugCount++;
        }

        // 9. 需求覆盖
        Set<Integer> reqIds = cases.stream().map(TestCase::getRequirementId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Map<String, Object>> reqList = new ArrayList<>();
        if (!reqIds.isEmpty() && requirementMapper != null) {
            List<Requirement> reqs = requirementMapper.selectBatchIds(reqIds);
            for (Requirement r : reqs) {
                Map<String, Object> m = new HashMap<>();
                m.put("reqCode", r.getReqCode());
                m.put("title", r.getTitle());
                reqList.add(m);
            }
        }

        // 10. 执行人员统计
        Map<Integer, Integer> userExecCount = new HashMap<>();
        for (TestPlanCase pc : planCases) {
            if (pc.getExecuteUserId() != null) {
                userExecCount.merge(pc.getExecuteUserId(), 1, Integer::sum);
            }
        }
        Map<Integer, String> userNameMap = new HashMap<>();
        if (!userExecCount.isEmpty()) {
            List<com.mokatest.platform.demos.domain.ui.User> users = userMapper.selectBatchIds(userExecCount.keySet());
            for (com.mokatest.platform.demos.domain.ui.User u : users) {
                userNameMap.put(u.getId().intValue(), u.getUsername());
            }
        }
        List<Map<String, Object>> executorList = new ArrayList<>();
        userExecCount.forEach((uid, count) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("userName", userNameMap.getOrDefault(uid, "用户" + uid));
            m.put("count", count);
            executorList.add(m);
        });

        // 11. 执行趋势（按天聚合）
        List<Map<String, Object>> trendList = new ArrayList<>();
        QueryWrapper<TestCaseExecution> trendWrapper = new QueryWrapper<>();
        trendWrapper.eq("plan_id", planId);
        trendWrapper.isNotNull("execute_time");
        trendWrapper.orderByAsc("execute_time");
        List<TestCaseExecution> execList = testCaseExecutionMapper.selectList(trendWrapper);
        if (!execList.isEmpty()) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            Map<String, int[]> trendMap = new LinkedHashMap<>();
            for (TestCaseExecution e : execList) {
                if (e.getExecuteTime() == null) continue;
                String day = sdf.format(e.getExecuteTime());
                int[] arr = trendMap.computeIfAbsent(day, k -> new int[4]); // 0:total 1:pass 2:fail 3:block
                arr[0]++;
                if ("PASS".equals(e.getResult())) arr[1]++;
                else if ("FAIL".equals(e.getResult())) arr[2]++;
                else if ("BLOCK".equals(e.getResult())) arr[3]++;
            }
            for (Map.Entry<String, int[]> entry : trendMap.entrySet()) {
                Map<String, Object> m = new HashMap<>();
                m.put("date", entry.getKey());
                m.put("total", entry.getValue()[0]);
                m.put("pass", entry.getValue()[1]);
                m.put("fail", entry.getValue()[2]);
                m.put("block", entry.getValue()[3]);
                trendList.add(m);
            }
        }

        // 组装报告
        // 缺陷列表快照：编号/名称/状态/处理人/用例数（bug_case 直接关联 + plan_case_id 计划关联去重计数）
        Set<Integer> bugAssigneeIds = bugs.stream().map(Bug::getAssigneeId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!bugAssigneeIds.isEmpty()) {
            List<com.mokatest.platform.demos.domain.ui.User> assignees = userMapper.selectBatchIds(bugAssigneeIds);
            for (com.mokatest.platform.demos.domain.ui.User u : assignees) {
                userNameMap.put(u.getId().intValue(), u.getUsername());
            }
        }
        Map<Integer, Long> bugDirectCaseCount = new HashMap<>();
        if (!bugs.isEmpty()) {
            QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> bcW = new QueryWrapper<>();
            bcW.in("bug_id", bugs.stream().map(Bug::getId).collect(Collectors.toList()));
            bugDirectCaseCount = bugCaseMapper.selectList(bcW).stream()
                    .collect(Collectors.groupingBy(com.mokatest.platform.demos.qa.domain.BugCase::getBugId, Collectors.counting()));
        }
        Map<Integer, Long> finalBugDirectCaseCount = bugDirectCaseCount;
        List<Map<String, Object>> bugList = bugs.stream().map(b -> {
            Map<String, Object> m = new HashMap<>();
            m.put("bugId", b.getId());
            m.put("bugCode", b.getBugCode());
            m.put("title", b.getTitle());
            m.put("status", b.getStatus());
            m.put("assigneeName", userNameMap.getOrDefault(b.getAssigneeId(), ""));
            long cnt = finalBugDirectCaseCount.getOrDefault(b.getId(), 0L) + (b.getPlanCaseId() != null ? 1 : 0);
            m.put("caseCount", cnt);
            return m;
        }).collect(Collectors.toList());

        // 用例明细快照：编号/名称/执行结果/等级/模块/执行人/缺陷数
        Map<Integer, Long> planCaseBugCount = bugs.stream()
                .filter(b -> b.getPlanCaseId() != null)
                .collect(Collectors.groupingBy(Bug::getPlanCaseId, Collectors.counting()));
        List<Map<String, Object>> caseDetails = new ArrayList<>();
        for (TestPlanCase pc : planCases) {
            TestCase tc = caseMap.get(pc.getTestCaseId());
            if (tc == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("planCaseId", pc.getId());
            m.put("testCaseId", tc.getId());
            m.put("caseCode", tc.getCaseCode());
            m.put("caseName", tc.getCaseName());
            m.put("executeResult", pc.getExecuteResult());
            m.put("priority", tc.getPriority());
            m.put("moduleName", moduleMap.getOrDefault(tc.getModuleId(), ""));
            m.put("executorName", userNameMap.getOrDefault(pc.getExecuteUserId(), ""));
            m.put("bugCount", planCaseBugCount.getOrDefault(pc.getId(), 0L));
            caseDetails.add(m);
        }

        PlanReportVO report = new PlanReportVO();
        report.setPlanId(plan.getId());
        report.setPlanName(plan.getPlanName());
        report.setPlanStatus(plan.getStatus());
        report.setStartTime(plan.getStartTime());
        report.setEndTime(plan.getEndTime());
        report.setDescription(plan.getDescription());
        report.setGenerateTime(new Date());

        report.setTotal(total);
        report.setPass(pass);
        report.setFail(fail);
        report.setBlock(block);
        report.setNa(na);
        report.setUnexec(unexec);
        report.setPassRate(total > unexec ? Math.round(pass * 100.0 / (total - unexec) * 10.0) / 10.0 : 0.0);
        report.setExecuteRate(total > 0 ? Math.round((total - unexec) * 100.0 / total * 10.0) / 10.0 : 0.0);

        report.setTotalBug(totalBug);
        report.setOpenBug(openBug);
        report.setBugBySeverity(bugBySeverity);
        report.setBugByStatus(bugByStatus);

        report.setFailCases(failCaseList);
        report.setModuleStats(new ArrayList<>(moduleStatMap.values()));
        report.setRequirements(reqList);
        report.setExecutors(executorList);
        report.setExecutionTrend(trendList);
        report.setBugList(bugList);
        report.setCaseDetails(caseDetails);

        return SaResult.ok().setData(report);
    }

    // VO 内部类
    @lombok.Data
    public static class TestPlanCaseVO {
        private Integer id;
        private Integer planId;
        private Integer testCaseId;
        private Integer sort;
        private String executeResult;
        private String executeRemark;
        private String stepResults;
        private Integer executeUserId;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date executeTime;
        // 用例信息
        private String caseCode;
        private String caseName;
        private String caseType;
        private String priority;
        private String preCondition;
        private java.util.List<com.mokatest.platform.demos.qa.domain.TestCase.TestStepItem> testSteps;
        // 关联BUG列表（一对多）
        private List<BugMiniVO> bugList;
        // 列表页扩展字段：测试点/模块/标签/人员/时间
        private Integer testPointId;
        private String testPointName;
        private String tags;
        private Integer moduleId;
        private String moduleName;
        private String createUserName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date createTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date updateTime;
        private String executeUserName;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BugMiniVO {
        private Integer id;
        private String bugCode;
        private String title;
        private String status;
        private String reporterName;
        private String assigneeName;
        // 关联用例数（bug_case 绑定数 + 来源计划用例）
        private Integer caseCount;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date createTime;
    }

    @lombok.Data
    public static class TestPlanListVO extends TestPlan {
        private String createUserName;
        private String moduleName;
        private Integer caseCount;
        private Integer passRate;
        /** 执行结果汇总：NONE（无用例/全部未执行）/ALL_PASS/NOT_PASS */
        private String execResult;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PlanDetailVO {
        private TestPlan plan;
        private List<TestPlanCaseVO> cases;
    }

    @lombok.Data
    public static class PlanReportVO {
        // 计划信息
        private Integer planId;
        private String planName;
        private String planStatus;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date startTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date endTime;
        private String description;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date generateTime;
        // 执行统计
        private int total;
        private int pass;
        private int fail;
        private int block;
        private int na;
        private int unexec;
        private double passRate;
        private double executeRate;
        // BUG统计
        private int totalBug;
        private int openBug;
        private Map<String, Long> bugBySeverity;
        private Map<String, Long> bugByStatus;
        // 明细
        private List<Map<String, Object>> failCases;
        private List<ModuleStat> moduleStats;
        private List<Map<String, Object>> requirements;
        private List<Map<String, Object>> executors;
        private List<Map<String, Object>> executionTrend;
        // 缺陷列表快照（编号/名称/状态/处理人/用例数）
        private List<Map<String, Object>> bugList;
        // 用例明细快照（编号/名称/执行结果/等级/模块/执行人/缺陷数）
        private List<Map<String, Object>> caseDetails;
    }

    @lombok.Data
    public static class ModuleStat {
        private String moduleName;
        private int total;
        private int pass;
        private int fail;
        private int block;
        private int bugCount;
    }

    @Override
    public SaResult stats(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("total", baseMapper.selectCount(new QueryWrapper<TestPlan>().eq("project_id", projectId)));
        map.put("running", baseMapper.selectCount(new QueryWrapper<TestPlan>().eq("project_id", projectId).eq("status", "RUNNING")));
        map.put("completed", baseMapper.selectCount(new QueryWrapper<TestPlan>().eq("project_id", projectId).eq("status", "COMPLETED")));
        map.put("draft", baseMapper.selectCount(new QueryWrapper<TestPlan>().eq("project_id", projectId).eq("status", "DRAFT")));
        return SaResult.data(map);
    }

    @Override
    public SaResult getTestPoints(Integer planId) {
        // 查测试点
        List<PlanTestPoint> tps = planTestPointMapper.selectList(
                new QueryWrapper<PlanTestPoint>().eq("plan_id", planId).orderByAsc("sort_order"));
        // 查计划用例（含 test_point_id）
        List<TestPlanCase> planCases = testPlanCaseMapper.selectList(
                new QueryWrapper<TestPlanCase>().eq("plan_id", planId));
        // 按 test_point_id 分组
        Map<Integer, List<Map<String, Object>>> caseGroup = new HashMap<>();
        List<Map<String, Object>> unassigned = new ArrayList<>();
        for (TestPlanCase pc : planCases) {
            Map<String, Object> caseInfo = new HashMap<>();
            caseInfo.put("planCaseId", pc.getId());
            caseInfo.put("testCaseId", pc.getTestCaseId());
            caseInfo.put("executeResult", pc.getExecuteResult());
            caseInfo.put("executeRemark", pc.getExecuteRemark());
            caseInfo.put("stepResults", pc.getStepResults());
            // 补用例名称
            TestCase tc = testCaseMapper.selectById(pc.getTestCaseId());
            caseInfo.put("caseName", tc != null ? tc.getCaseName() : "已删除");
            caseInfo.put("priority", tc != null ? tc.getPriority() : null);
            if (pc.getTestPointId() != null) {
                caseGroup.computeIfAbsent(pc.getTestPointId(), k -> new ArrayList<>()).add(caseInfo);
            } else {
                unassigned.add(caseInfo);
            }
        }
        // 组装结果
        List<Map<String, Object>> result = new ArrayList<>();
        for (PlanTestPoint tp : tps) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", tp.getId());
            m.put("name", tp.getName());
            m.put("sortOrder", tp.getSortOrder());
            m.put("cases", caseGroup.getOrDefault(tp.getId(), Collections.emptyList()));
            result.add(m);
        }
        // 未分配用例单独返回
        Map<String, Object> data = new HashMap<>();
        data.put("testPoints", result);
        data.put("unassignedCases", unassigned);
        return SaResult.data(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult saveTestPoints(Integer planId, List<Map<String, Object>> testPoints) {
        // 查现有测试点
        List<PlanTestPoint> existing = planTestPointMapper.selectList(
                new QueryWrapper<PlanTestPoint>().eq("plan_id", planId));
        Set<Integer> existingIds = existing.stream().map(PlanTestPoint::getId).collect(Collectors.toSet());
        Set<Integer> incomingIds = new HashSet<>();

        for (int i = 0; i < testPoints.size(); i++) {
            Map<String, Object> tp = testPoints.get(i);
            Integer id = tp.get("id") != null ? ((Number) tp.get("id")).intValue() : null;
            String name = (String) tp.get("name");
            @SuppressWarnings("unchecked")
            List<Number> caseIdNums = tp.get("caseIds") != null ? (List<Number>) tp.get("caseIds") : Collections.emptyList();
            List<Integer> caseIds = caseIdNums.stream().map(n -> n != null ? n.intValue() : null).collect(Collectors.toList());

            if (id != null && existingIds.contains(id)) {
                // 更新
                PlanTestPoint update = new PlanTestPoint();
                update.setId(id);
                update.setName(name);
                update.setSortOrder(i);
                planTestPointMapper.updateById(update);
                incomingIds.add(id);
                // 更新用例关联
                updateCaseTestPoint(planId, id, caseIds);
            } else {
                // 新增
                PlanTestPoint newTp = new PlanTestPoint();
                newTp.setPlanId(planId);
                newTp.setName(name);
                newTp.setSortOrder(i);
                newTp.setCreateTime(new Date());
                planTestPointMapper.insert(newTp);
                updateCaseTestPoint(planId, newTp.getId(), caseIds);
            }
        }

        // 删除：现有但不在传入列表中的
        for (PlanTestPoint tp : existing) {
            if (!incomingIds.contains(tp.getId())) {
                planTestPointMapper.deleteById(tp.getId());
                // 解除用例关联
                List<TestPlanCase> cases = testPlanCaseMapper.selectList(
                        new QueryWrapper<TestPlanCase>().eq("plan_id", planId).eq("test_point_id", tp.getId()));
                for (TestPlanCase pc : cases) {
                    pc.setTestPointId(null);
                    testPlanCaseMapper.updateById(pc);
                }
            }
        }
        return SaResult.ok("保存成功");
    }

    @Override
    public SaResult updateCaseTestPoint(Integer planId, Integer planCaseId, Integer testPointId) {
        TestPlanCase pc = testPlanCaseMapper.selectById(planCaseId);
        if (pc == null || !planId.equals(pc.getPlanId())) {
            return SaResult.error("用例不存在");
        }
        pc.setTestPointId(testPointId); // null = 取消关联
        testPlanCaseMapper.updateById(pc);
        return SaResult.ok("操作成功");
    }

    private void updateCaseTestPoint(Integer planId, Integer testPointId, List<Integer> caseIds) {
        // 先解除旧关联
        List<TestPlanCase> oldCases = testPlanCaseMapper.selectList(
                new QueryWrapper<TestPlanCase>().eq("plan_id", planId).eq("test_point_id", testPointId));
        for (TestPlanCase pc : oldCases) {
            pc.setTestPointId(null);
            testPlanCaseMapper.updateById(pc);
        }
        // 建立新关联：按 testCaseId 匹配 plan_case
        for (Integer testCaseId : caseIds) {
            TestPlanCase pc = testPlanCaseMapper.selectOne(
                    new QueryWrapper<TestPlanCase>().eq("plan_id", planId).eq("test_case_id", testCaseId));
            if (pc != null) {
                pc.setTestPointId(testPointId);
                testPlanCaseMapper.updateById(pc);
            }
        }
    }
}
