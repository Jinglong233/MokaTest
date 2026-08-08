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
    private final com.mokatest.platform.demos.mapper.UserMapper userMapper;
    private final com.mokatest.platform.demos.qa.service.BugService bugService;
    private final QaModuleMapper qaModuleMapper;
    private final RequirementMapper requirementMapper;

    @Override
    public SaResult listByProject(Integer projectId, String keyword, String status, Integer page, Integer pageSize) {
        QueryWrapper<TestPlan> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("plan_name", keyword);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("create_time");
        Page<TestPlan> pageParam = new Page<>(page, pageSize);
        Page<TestPlan> result = testPlanMapper.selectPage(pageParam, wrapper);
        return SaResult.ok().setData(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult saveOrUpdatePlan(TestPlan plan, Integer loginUserId) {
        if (plan.getId() == null) {
            plan.setCreateUserId(loginUserId);
            plan.setStatus("DRAFT");
            plan.setCreateTime(new Date());
        }
        plan.setUpdateTime(new Date());
        boolean success = saveOrUpdate(plan);
        return success ? SaResult.ok("保存成功").setData(plan) : SaResult.error("保存失败");
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
            bugMap.putAll(bugs.stream()
                    .collect(Collectors.groupingBy(Bug::getPlanCaseId,
                            Collectors.mapping(b -> new BugMiniVO(b.getId(), b.getBugCode(), b.getTitle(), b.getStatus()),
                                    Collectors.toList()))));
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
            vo.setExecuteUserId(pc.getExecuteUserId());
            vo.setExecuteTime(pc.getExecuteTime());
            // 填充用例信息
            TestCase tc = cases.stream().filter(c -> c.getId().equals(pc.getTestCaseId())).findFirst().orElse(null);
            if (tc != null) {
                vo.setCaseCode(tc.getCaseCode());
                vo.setCaseName(tc.getCaseName());
                vo.setCaseType(tc.getCaseType());
                vo.setPriority(tc.getPriority());
                vo.setPreCondition(tc.getPreCondition());
                vo.setTestSteps(tc.getTestSteps());
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
    public SaResult executeCase(Integer planCaseId, String result, String remark, Integer executeUserId) {
        TestPlanCase pc = testPlanCaseMapper.selectById(planCaseId);
        if (pc == null) {
            return SaResult.error("计划用例不存在");
        }
        // 更新计划用例执行结果
        pc.setExecuteResult(result);
        pc.setExecuteRemark(remark);
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
        bug.setTestCaseId(testCase.getId());
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
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BugMiniVO {
        private Integer id;
        private String bugCode;
        private String title;
        private String status;
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
}
