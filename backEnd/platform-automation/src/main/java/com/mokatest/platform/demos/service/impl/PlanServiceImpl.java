package com.mokatest.platform.demos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.uiEnum.plan.PlanCategory;
import com.mokatest.platform.demos.domain.ui.uiEnum.task.TaskExecuteStatus;
import com.mokatest.platform.demos.domain.ui.Plan;
import com.mokatest.platform.demos.domain.ui.Report;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.domain.ui.SceneSetting;
import com.google.gson.reflect.TypeToken;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.exception.ParamIsEmptyException;
import com.mokatest.platform.demos.manager.TaskManager;
import com.mokatest.platform.demos.mapper.PlanMapper;
import com.mokatest.platform.demos.mapper.ReportMapper;
import com.mokatest.platform.demos.mapper.SceneMapper;
import com.mokatest.platform.demos.service.PlanService;
import com.mokatest.platform.demos.listener.projectListener.Enum.UpdateDataType;
import com.mokatest.platform.demos.listener.projectListener.ProjectUpdateEvent;
import com.google.gson.Gson;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;


/**
 * @author: JingLong
 * @description 针对表【plan】的数据库操作Service实现
 * @createDate 2025-09-23 16:35:14
 */
@Service
public class PlanServiceImpl extends ServiceImpl<PlanMapper, Plan> implements PlanService {
    @Autowired
    private TaskManager taskManager;

    @Resource
    private PlanMapper planMapper;

    @Resource
    private SceneMapper sceneMapper;
    @Autowired
    private ReportMapper reportMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;


    @Override
    public Boolean addPlan(Plan plan) {
        if (plan == null) {
            throw new ParamIsEmptyException("缺少参数");
        }

        // 创建计划配置
        plan.setPlanRunningSetting(new Gson().toJson(new SceneSetting()));
        plan.setStatus(TaskExecuteStatus.NOT_STARTED);
        // 默认关闭 Webhook 通知，用户可在计划详情页手动开启
        plan.setWebhookEnabled(0);
        // 默认不关联任何 Webhook 配置（null 表示发送该项目下所有启用的配置）
        plan.setWebhookIds(null);

        // 处理计划类型
        plan.setPlanCategory(normalizePlanCategory(plan.getPlanCategory()));

        // todo 创建者id
        plan.setCreateUserId(11);
        plan.setUpdateUserId(11);

        // 插入
        int insert = planMapper.insert(plan);
        if (insert <= 0) {
            return false;
        }
        // 同步更新项目计划数量
        if (plan.getProjectId() != null) {
            eventPublisher.publishEvent(new ProjectUpdateEvent(this, 1, String.valueOf(plan.getProjectId()), UpdateDataType.PLAN));
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean stopPlan(Integer planId) {
        if (planId == null) {
            throw new ParamIsEmptyException("缺少参数");
        }

        // 先判断是否是激活状态
        Plan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new ParamIsEmptyException("计划不存在");
        }
        if (plan.getIsActive() == 1) {
            plan.setIsActive(0);
            plan.setStatus(TaskExecuteStatus.NOT_STARTED);
            // 更新
            int update = planMapper.updateById(plan);
            if (update <= 0) {
                return false;
            }
        }
        return taskManager.stopTask(planId);
    }

    @Override
    public Integer executeTask(Integer taskId) {
        if (taskId == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        Plan plan = planMapper.selectById(taskId);
        if (plan == null) {
            throw new BusinessException("计划不存在");
        }
        plan.setPlanCategory(normalizePlanCategory(plan.getPlanCategory()));
        validateScenesMatchCategory(plan);
        return taskManager.executeNow(taskId);
    }

    @Override
    public Map<Integer, Object> getTaskStatus() {
        return taskManager.getTaskStatus();
    }


    @Override
    public List<Plan> allPlan(Integer projectId) {
        if (projectId == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        QueryWrapper<Plan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("project_id", projectId);
        queryWrapper.orderByDesc("created_at");
        List<Plan> plans = planMapper.selectList(queryWrapper);
        for (Plan plan : plans) {
            if (plan != null && (plan.getPlanCategory() == null || plan.getPlanCategory().isBlank())) {
                plan.setPlanCategory(PlanCategory.UI.name());
            }
        }
        return plans;
    }

    @Override
    public Plan getPlanById(Integer planId) {
        Plan plan = planMapper.selectById(planId);
        if (plan != null && (plan.getPlanCategory() == null || plan.getPlanCategory().isBlank())) {
            plan.setPlanCategory(PlanCategory.UI.name());
        }
        return plan;
    }

    @Override
    @Transactional
    public Boolean updatePlan(Plan plan) {
        if (plan == null) {
            throw new ParamIsEmptyException("缺少参数");
        }

        Plan exist = planMapper.selectById(plan.getId());
        if (exist == null) {
            throw new BusinessException("计划不存在");
        }

        // 类型不可清空，前端未传时保持原值
        if (plan.getPlanCategory() == null || plan.getPlanCategory().isBlank()) {
            plan.setPlanCategory(exist.getPlanCategory());
        } else {
            plan.setPlanCategory(normalizePlanCategory(plan.getPlanCategory()));
        }

        // 修改关联场景时校验场景分类与计划分类一致
        if (plan.getParams() != null) {
            validateScenesMatchCategory(plan);
        }

        int update = planMapper.updateById(plan);
        // 判断改计划是否是激活状态
        if (plan.getIsActive() == 1 && TaskExecuteStatus.IN_PROGRESS.equals(TaskExecuteStatus.valueOf(plan.getStatus().toString()))) {
            Boolean aBoolean = taskManager.updateTask(plan);
            if (!aBoolean) {
                throw new RuntimeException("更新计划失败");
            }
        }
        return update > 0;
    }

    @Override
    @Transactional
    public Boolean deletePlan(Integer planId) {
        if (planId == null) {
            throw new ParamIsEmptyException("缺少参数");
        }

        Plan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new ParamIsEmptyException("计划不存在");
        }
        if (plan.getIsActive() == 1 && TaskExecuteStatus.IN_PROGRESS.equals(TaskExecuteStatus.valueOf(plan.getStatus().toString()))) {
            // 只有执行中的任务才对其进行任务提示
            Boolean aBoolean = taskManager.stopTask(planId);
            if (!aBoolean) {
                throw new RuntimeException("计划停止失败");
            }
        }
        Integer projectId = plan.getProjectId();
        plan.setDeletedAt(new Date());
        int delete = planMapper.deleteById(plan);
        if (delete > 0 && projectId != null) {
            eventPublisher.publishEvent(new ProjectUpdateEvent(this, -1, String.valueOf(projectId), UpdateDataType.PLAN));
        }
        return delete > 0;
    }

    @Override
    @Transactional
    public Boolean activeTask(Integer planId) {
        if (planId == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        Plan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new ParamIsEmptyException("计划不存在");
        }
        // 兜底历史空值
        plan.setPlanCategory(normalizePlanCategory(plan.getPlanCategory()));

        // 判断是否是执行中（也就是在执行中 或者 正在等待执行）
        if (plan.getStatus() == TaskExecuteStatus.IN_PROGRESS) {
            // 返回提示
            throw new RuntimeException("计划正在执行中，完成执行尝试操作！");
        }
        if (TaskExecuteStatus.NOT_STARTED.equals(TaskExecuteStatus.valueOf(plan.getStatus().toString())) && plan.getIsActive() == 0) {
            // 未开始就执行操作
            // 1. 查询计划里边是否有可执行场景
            Object params = plan.getParams();
            if (params == null) {
                throw new BusinessException("当前计划场景为空，请先添加可执行场景！");
            }
            List<Integer> scenesList = new Gson().fromJson(params.toString(), List.class);
            if (scenesList.isEmpty()) {
                throw new BusinessException("当前计划场景为空，请先添加可执行场景！");
            }
            validateScenesMatchCategory(plan);
            // 修改计划状态为激活
            plan.setIsActive(1);
            int update = planMapper.updateById(plan);
            if (update <= 0) {
                throw new RuntimeException("计划状态修改失败！");
            }
            return taskManager.addTask(plan);
        }
        return false;
    }

    @Override
    public Boolean reRun(Map<String, Object> reTryScenes) {
        if (reTryScenes == null || reTryScenes.get("reTrySceneIds") == null || reTryScenes.get("sourceReportId") == null) {
            return true;
        }
        // 获取失败场景Id
        List<Integer> reTrySceneIds = (List<Integer>) reTryScenes.get("reTrySceneIds");
        // 优先查询数据库scene，判断是否存在
        List<Scene> scenes = sceneMapper.selectBatchIds(reTrySceneIds);
        if (scenes.size() != reTrySceneIds.size()) {
            throw new BusinessException("部分场景不存在！");
        }
        Integer reportId = Integer.valueOf(reTryScenes.get("sourceReportId").toString());
        // 查询对应计划是否存在
        Report updateReport = reportMapper.selectById(reportId);
        if (updateReport == null) {
            throw new BusinessException("报告不存在！");
        }
        Plan plan = planMapper.selectById(updateReport.getPlanId());
        if (plan == null) {
            throw new BusinessException("计划不存在！");
        }
        String planCategory = normalizePlanCategory(plan.getPlanCategory());
        for (Scene scene : scenes) {
            String sceneCat = scene.getSceneCategory() == null ? PlanCategory.UI.name() : scene.getSceneCategory();
            if (!sceneCat.equals(planCategory)) {
                throw new BusinessException("重跑场景类型与计划类型不一致");
            }
        }
        return taskManager.reExecuteFailedScenario(reportId, reTrySceneIds);
    }

    @Override
    public Boolean updatePlanRunningConfig(Map<String, Object> sceneSetting) {
        if (sceneSetting == null || sceneSetting.isEmpty()) return true;
        String planId = sceneSetting.get("planId").toString();
        if (planId == null || "".equals(planId)) throw new ParamIsEmptyException("缺少计划id");
        Plan plan = planMapper.selectById(planId);
        if (plan == null) throw new RuntimeException("计划不存在");
        String planRunningSettingStr = sceneSetting.get("planRunningSetting").toString();
        if (planRunningSettingStr == null || "".equals(planRunningSettingStr)) return true;
        plan.setPlanRunningSetting(planRunningSettingStr);
        return planMapper.updateById(plan) > 0;
    }


    /**
     * 规范化计划类型：空值默认 UI，仅允许 UI/API（一期）
     */
    private String normalizePlanCategory(String category) {
        if (category == null || category.isBlank()) {
            return PlanCategory.UI.name();
        }
        String upper = category.trim().toUpperCase();
        if (!PlanCategory.UI.name().equals(upper) && !PlanCategory.API.name().equals(upper)) {
            throw new BusinessException("计划类型只支持 UI/API");
        }
        return upper;
    }

    /**
     * 解析 plan.params 中的场景 ID 列表
     */
    private List<Integer> parseSceneIds(Plan plan) {
        Object params = plan.getParams();
        if (params == null) {
            return new ArrayList<>();
        }
        java.lang.reflect.Type listType = new TypeToken<List<Integer>>() {
        }.getType();
        List<Integer> list = new Gson().fromJson(params.toString(), listType);
        return list == null ? new ArrayList<>() : list;
    }

    /**
     * 校验计划关联的场景类型与计划类型一致
     */
    private void validateScenesMatchCategory(Plan plan) {
        String planCategory = normalizePlanCategory(plan.getPlanCategory());
        List<Integer> sceneIds = parseSceneIds(plan);
        if (sceneIds.isEmpty()) {
            return;
        }
        List<Scene> scenes = sceneMapper.selectBatchIds(sceneIds);
        for (Scene scene : scenes) {
            String sceneCat = scene.getSceneCategory() == null ? PlanCategory.UI.name() : scene.getSceneCategory();
            if (!sceneCat.equals(planCategory)) {
                throw new BusinessException("计划类型与场景类型不一致：场景[" + scene.getName() + "]为 " + sceneCat + "，计划为 " + planCategory);
            }
        }
    }


}




