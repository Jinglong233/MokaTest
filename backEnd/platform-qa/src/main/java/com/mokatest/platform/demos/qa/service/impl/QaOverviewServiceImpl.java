package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.qa.domain.*;
import com.mokatest.platform.demos.qa.mapper.*;
import com.mokatest.platform.demos.qa.service.QaOverviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaOverviewServiceImpl implements QaOverviewService {

    private final RequirementMapper requirementMapper;
    private final TestCaseMapper testCaseMapper;
    private final BugMapper bugMapper;
    private final TestPlanMapper testPlanMapper;
    private final TestPlanCaseMapper testPlanCaseMapper;
    private final com.mokatest.platform.demos.mapper.UserMapper userMapper;

    @Override
    public SaResult getProjectOverview(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }

        Map<String, Object> result = new HashMap<>();

        // 1. 需求统计
        QueryWrapper<Requirement> reqWrapper = new QueryWrapper<>();
        reqWrapper.eq("project_id", projectId);
        long reqTotal = requirementMapper.selectCount(reqWrapper);
        result.put("reqTotal", reqTotal);

        // 2. 用例统计
        QueryWrapper<TestCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("project_id", projectId);
        long caseTotal = testCaseMapper.selectCount(caseWrapper);
        result.put("caseTotal", caseTotal);

        // 3. BUG 统计
        QueryWrapper<Bug> bugWrapper = new QueryWrapper<>();
        bugWrapper.eq("project_id", projectId);
        long bugTotal = bugMapper.selectCount(bugWrapper);
        result.put("bugTotal", bugTotal);

        // 未关闭 BUG
        QueryWrapper<Bug> openBugWrapper = new QueryWrapper<>();
        openBugWrapper.eq("project_id", projectId);
        openBugWrapper.notIn("status", Arrays.asList("CLOSED", "REJECTED"));
        long openBugTotal = bugMapper.selectCount(openBugWrapper);
        result.put("openBugTotal", openBugTotal);

        // 4. 测试计划统计
        QueryWrapper<TestPlan> planWrapper = new QueryWrapper<>();
        planWrapper.eq("project_id", projectId);
        long planTotal = testPlanMapper.selectCount(planWrapper);
        result.put("planTotal", planTotal);

        // 5. BUG 严重程度分布
        QueryWrapper<Bug> bugSevWrapper = new QueryWrapper<>();
        bugSevWrapper.eq("project_id", projectId);
        bugSevWrapper.select("severity", "count(*) as cnt");
        bugSevWrapper.groupBy("severity");
        List<Map<String, Object>> bugSevList = bugMapper.selectMaps(bugSevWrapper);
        Map<String, Long> bugBySeverity = new HashMap<>();
        for (Map<String, Object> m : bugSevList) {
            String sev = (String) m.get("severity");
            Long cnt = ((Number) m.get("cnt")).longValue();
            bugBySeverity.put(sev != null ? sev : "UNKNOWN", cnt);
        }
        result.put("bugBySeverity", bugBySeverity);

        // 6. BUG 状态分布
        QueryWrapper<Bug> bugStatusWrapper = new QueryWrapper<>();
        bugStatusWrapper.eq("project_id", projectId);
        bugStatusWrapper.select("status", "count(*) as cnt");
        bugStatusWrapper.groupBy("status");
        List<Map<String, Object>> bugStatusList = bugMapper.selectMaps(bugStatusWrapper);
        Map<String, Long> bugByStatus = new HashMap<>();
        for (Map<String, Object> m : bugStatusList) {
            String status = (String) m.get("status");
            Long cnt = ((Number) m.get("cnt")).longValue();
            bugByStatus.put(status != null ? status : "UNKNOWN", cnt);
        }
        result.put("bugByStatus", bugByStatus);

        // 7. 最近 BUG（5条）
        QueryWrapper<Bug> recentBugWrapper = new QueryWrapper<>();
        recentBugWrapper.eq("project_id", projectId);
        recentBugWrapper.orderByDesc("create_time");
        recentBugWrapper.last("LIMIT 5");
        List<Bug> recentBugs = bugMapper.selectList(recentBugWrapper);
        result.put("recentBugs", convertBugList(recentBugs));

        // 8. 最近需求（5条）
        QueryWrapper<Requirement> recentReqWrapper = new QueryWrapper<>();
        recentReqWrapper.eq("project_id", projectId);
        recentReqWrapper.orderByDesc("create_time");
        recentReqWrapper.last("LIMIT 5");
        List<Requirement> recentReqs = requirementMapper.selectList(recentReqWrapper);
        result.put("recentRequirements", convertReqList(recentReqs));

        // 9. 最近测试计划（3条）+ 执行统计
        QueryWrapper<TestPlan> recentPlanWrapper = new QueryWrapper<>();
        recentPlanWrapper.eq("project_id", projectId);
        recentPlanWrapper.orderByDesc("create_time");
        recentPlanWrapper.last("LIMIT 3");
        List<TestPlan> recentPlans = testPlanMapper.selectList(recentPlanWrapper);
        result.put("recentPlans", convertPlanList(recentPlans));

        return SaResult.ok().setData(result);
    }

    private List<Map<String, Object>> convertBugList(List<Bug> bugs) {
        if (bugs.isEmpty()) return Collections.emptyList();
        // 查询用户信息
        Set<Integer> userIds = bugs.stream().map(Bug::getReporterId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<com.mokatest.platform.demos.domain.ui.User> users = userMapper.selectBatchIds(userIds);
            for (com.mokatest.platform.demos.domain.ui.User u : users) {
                userMap.put(u.getId().intValue(), u.getUsername());
            }
        }
        return bugs.stream().map(b -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId());
            m.put("bugCode", b.getBugCode());
            m.put("title", b.getTitle());
            m.put("severity", b.getSeverity());
            m.put("status", b.getStatus());
            m.put("reporterName", userMap.getOrDefault(b.getReporterId(), ""));
            m.put("createTime", b.getCreateTime());
            return m;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> convertReqList(List<Requirement> reqs) {
        if (reqs.isEmpty()) return Collections.emptyList();
        return reqs.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("reqCode", r.getReqCode());
            m.put("title", r.getTitle());
            m.put("status", r.getStatus());
            m.put("priority", r.getPriority());
            m.put("createTime", r.getCreateTime());
            return m;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> convertPlanList(List<TestPlan> plans) {
        if (plans.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (TestPlan plan : plans) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", plan.getId());
            m.put("planName", plan.getPlanName());
            m.put("status", plan.getStatus());
            m.put("startTime", plan.getStartTime());
            m.put("endTime", plan.getEndTime());

            // 统计该计划的执行结果
            QueryWrapper<TestPlanCase> tpcWrapper = new QueryWrapper<>();
            tpcWrapper.eq("plan_id", plan.getId());
            List<TestPlanCase> planCases = testPlanCaseMapper.selectList(tpcWrapper);
            int total = planCases.size();
            int pass = 0, fail = 0, block = 0, unexec = 0;
            for (TestPlanCase pc : planCases) {
                String r = pc.getExecuteResult();
                if ("PASS".equals(r)) pass++;
                else if ("FAIL".equals(r)) fail++;
                else if ("BLOCK".equals(r)) block++;
                else unexec++;
            }
            m.put("caseTotal", total);
            m.put("pass", pass);
            m.put("fail", fail);
            m.put("block", block);
            m.put("unexec", unexec);
            m.put("executeRate", total > 0 ? Math.round((total - unexec) * 100.0 / total * 10.0) / 10.0 : 0.0);
            list.add(m);
        }
        return list;
    }
}
