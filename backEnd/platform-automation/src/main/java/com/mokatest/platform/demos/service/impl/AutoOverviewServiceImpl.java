package com.mokatest.platform.demos.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import com.mokatest.platform.demos.domain.ui.Plan;
import com.mokatest.platform.demos.domain.ui.Project;
import com.mokatest.platform.demos.domain.ui.Report;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.mapper.PlanMapper;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import com.mokatest.platform.demos.mapper.ReportMapper;
import com.mokatest.platform.demos.mapper.SceneMapper;
import com.mokatest.platform.demos.service.AutoOverviewService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AutoOverviewServiceImpl implements AutoOverviewService {

    @Resource
    private SceneMapper sceneMapper;

    @Resource
    private ApiRequestMapper apiRequestMapper;

    @Resource
    private PlanMapper planMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ReportMapper reportMapper;

    @Override
    public SaResult getAutoOverview(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }

        Map<String, Object> result = new HashMap<>();

        // 1. UI 场景数
        QueryWrapper<Scene> uiSceneWrapper = new QueryWrapper<>();
        uiSceneWrapper.eq("project_id", projectId);
        uiSceneWrapper.eq("scene_type", "SCENE");
        uiSceneWrapper.eq("scene_category", "UI");
        long uiSceneCount = sceneMapper.selectCount(uiSceneWrapper);
        result.put("uiSceneCount", uiSceneCount);

        // 2. API 场景数
        QueryWrapper<Scene> apiSceneWrapper = new QueryWrapper<>();
        apiSceneWrapper.eq("project_id", projectId);
        apiSceneWrapper.eq("scene_type", "SCENE");
        apiSceneWrapper.eq("scene_category", "API");
        long apiSceneCount = sceneMapper.selectCount(apiSceneWrapper);
        result.put("apiSceneCount", apiSceneCount);

        // 3. API 用例数（source_drat_id 不为空且不为 0）
        QueryWrapper<ApiRequest> apiCaseWrapper = new QueryWrapper<>();
        apiCaseWrapper.eq("project_id", projectId);
        apiCaseWrapper.isNotNull("source_drat_id");
        apiCaseWrapper.ne("source_drat_id", 0);
        long apiCaseCount = apiRequestMapper.selectCount(apiCaseWrapper);
        result.put("apiCaseCount", apiCaseCount);

        // 4. 自动化任务数
        QueryWrapper<Plan> planWrapper = new QueryWrapper<>();
        planWrapper.eq("project_id", projectId);
        long planTotal = planMapper.selectCount(planWrapper);
        result.put("planTotal", planTotal);

        // 5. 项目静态字段（覆盖率、通过率、性能测试数）
        Project project = projectMapper.selectById(projectId);
        if (project != null) {
            result.put("coverage", project.getCoverage() != null ? project.getCoverage() : 0);
            result.put("uiPass", project.getUiPass() != null ? project.getUiPass() : 0);
            result.put("performanceTotal", project.getPerformanceTotal() != null ? project.getPerformanceTotal() : 0);
        } else {
            result.put("coverage", 0);
            result.put("uiPass", 0);
            result.put("performanceTotal", 0);
        }

        // 6. 今日执行次数与 7 天趋势、实时队列、30 天 UI 平均通过率
        enrichExecutionStats(result, projectId);

        return SaResult.ok().setData(result);
    }

    /**
     * 补充执行侧统计：今日执行次数、近 30 天 UI 平均通过率、7 天执行趋势、实时运行队列。
     */
    private void enrichExecutionStats(Map<String, Object> result, Integer projectId) {
        // 拉取最近 30 天报告（含运行中），用于趋势与实时队列
        Calendar cal = Calendar.getInstance();
        Date endDate = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR, -29);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startDate = cal.getTime();

        QueryWrapper<Report> reportWrapper = new QueryWrapper<>();
        reportWrapper.eq("project_id", projectId);
        reportWrapper.ge("create_time", startDate);
        reportWrapper.le("create_time", endDate);
        reportWrapper.orderByDesc("create_time");
        List<Report> recentReports = reportMapper.selectList(reportWrapper);

        // 今日执行次数
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        long todayExecuteCount = recentReports.stream()
                .filter(r -> r.getCreateTime() != null &&
                        new SimpleDateFormat("yyyy-MM-dd").format(r.getCreateTime()).equals(today))
                .count();
        result.put("todayExecuteCount", todayExecuteCount);

        // 最近 30 天 UI 平均通过率（按报告成功率：成功 UI 报告 / 已完成 UI 报告总数）
        List<Report> completedUiReports30d = recentReports.stream()
                .filter(r -> "UI".equals(r.getReportCategory())
                        && r.getStatus() != null
                        && r.getStatus() == 1)
                .collect(Collectors.toList());
        long uiSuccessReports = completedUiReports30d.stream()
                .filter(this::isReportSuccess)
                .count();
        int uiAvgPassRate30d = completedUiReports30d.isEmpty()
                ? 0
                : (int) (uiSuccessReports * 100 / completedUiReports30d.size());
        result.put("uiAvgPassRate30d", uiAvgPassRate30d);

        // 7 天执行趋势（按 create_time 日期聚合：总次数 = 已完成报告数，成功/失败按报告整体判定）
        List<Map<String, Object>> sevenDayTrend = buildSevenDayTrend(recentReports);
        result.put("sevenDayTrend", sevenDayTrend);

        // 实时运行队列（status = 0 表示执行中）
        List<Map<String, Object>> runningQueue = recentReports.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 0)
                .sorted(Comparator.comparing(Report::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::buildRunningReportItem)
                .collect(Collectors.toList());
        result.put("runningQueue", runningQueue);
    }

    private List<Map<String, Object>> buildSevenDayTrend(List<Report> recentReports) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd");

        // 生成最近 7 天日期
        Map<String, Map<String, Object>> dateMap = new LinkedHashMap<>();
        Calendar cal = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            String dateKey = dateFormat.format(cal.getTime());
            Map<String, Object> item = new HashMap<>();
            item.put("date", dateKey);
            item.put("displayDate", displayFormat.format(cal.getTime()));
            item.put("total", 0L);
            item.put("success", 0L);
            item.put("fail", 0L);
            dateMap.put(dateKey, item);
        }

        for (Report r : recentReports) {
            // 仅统计已完成的报告
            if (r.getCreateTime() == null || r.getStatus() == null || r.getStatus() != 1) continue;
            String dateKey = dateFormat.format(r.getCreateTime());
            Map<String, Object> item = dateMap.get(dateKey);
            if (item == null) continue;
            item.put("total", (Long) item.get("total") + 1);
            if (isReportSuccess(r)) {
                item.put("success", (Long) item.get("success") + 1);
            } else {
                item.put("fail", (Long) item.get("fail") + 1);
            }
        }

        return new ArrayList<>(dateMap.values());
    }

    /**
     * 判定报告是否成功：无失败步骤且至少有一个成功步骤。
     * 全跳过/无步骤的报告视为不成功，不计入成功统计。
     */
    private boolean isReportSuccess(Report report) {
        int error = report.getStepErrorNumber() == null ? 0 : report.getStepErrorNumber();
        int success = report.getStepSuccessNumber() == null ? 0 : report.getStepSuccessNumber();
        return error == 0 && success > 0;
    }

    private Map<String, Object> buildRunningReportItem(Report report) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", report.getId());
        item.put("planId", report.getPlanId());
        item.put("reportName", report.getReportName());
        item.put("planName", report.getPlanName());
        item.put("reportCategory", report.getReportCategory());
        item.put("createTime", report.getCreateTime());

        // 进度估算：已产出步骤 / 总步骤
        int total = report.getStepNumber() == null ? 0 : report.getStepNumber();
        int finished = (report.getStepSuccessNumber() == null ? 0 : report.getStepSuccessNumber())
                + (report.getStepErrorNumber() == null ? 0 : report.getStepErrorNumber())
                + (report.getStepSkipNumber() == null ? 0 : report.getStepSkipNumber());
        int progress = total > 0 ? Math.min(99, (int) (finished * 100 / total)) : 45;
        item.put("progress", progress);
        return item;
    }
}
