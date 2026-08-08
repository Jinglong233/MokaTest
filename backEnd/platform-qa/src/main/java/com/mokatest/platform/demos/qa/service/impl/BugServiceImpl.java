package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import com.mokatest.platform.demos.mapper.TeamMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.qa.domain.Bug;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.vo.BugVO;
import com.mokatest.platform.demos.qa.mapper.BugMapper;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import com.mokatest.platform.demos.qa.message.dto.MessageContext;
import com.mokatest.platform.demos.qa.message.enums.MessageEventType;
import com.mokatest.platform.demos.qa.message.service.MessageService;
import com.mokatest.platform.demos.qa.service.BugService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * BUG池 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BugServiceImpl extends ServiceImpl<BugMapper, Bug> implements BugService {

    private final UserMapper userMapper;
    private final RequirementMapper requirementMapper;
    private final TestCaseMapper testCaseMapper;
    private final ProjectMapper projectMapper;
    private final TeamMapper teamMapper;
    private final com.mokatest.platform.demos.qa.mapper.QaModuleMapper qaModuleMapper;
    private final com.mokatest.platform.demos.qa.mapper.TestPlanCaseMapper testPlanCaseMapper;
    private final com.mokatest.platform.demos.qa.mapper.TestPlanMapper testPlanMapper;
    private final com.mokatest.platform.demos.qa.service.BugOperationLogService bugOperationLogService;
    private final com.mokatest.platform.demos.qa.mapper.BugCommentMapper bugCommentMapper;
    private final com.mokatest.platform.demos.qa.mapper.BugOperationLogMapper bugOperationLogMapper;
    private final MessageService messageService;
    private final com.mokatest.platform.demos.qa.message.service.impl.NotifyDispatchService notifyDispatchService;

    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            "NEW", "CONFIRMED", "FIXING", "FIXED", "VERIFIED", "CLOSED", "REJECTED"
    ));

    @Override
    public SaResult listByProject(Integer projectId, String keyword, String status, String severity, String priority, Integer requirementId, Integer testCaseId, Integer moduleId, String environment, String reproduceRate, String closeReason, Integer page, Integer pageSize) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        QueryWrapper<Bug> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("title", keyword).or().like("bug_code", keyword));
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        if (severity != null && !severity.isEmpty()) {
            wrapper.eq("severity", severity);
        }
        if (priority != null && !priority.isEmpty()) {
            wrapper.eq("priority", priority);
        }
        if (requirementId != null) {
            wrapper.eq("requirement_id", requirementId);
        }
        if (testCaseId != null) {
            wrapper.eq("test_case_id", testCaseId);
        }
        if (moduleId != null) {
            wrapper.eq("module_id", moduleId);
        }
        if (environment != null && !environment.isEmpty()) {
            wrapper.eq("environment", environment);
        }
        if (reproduceRate != null && !reproduceRate.isEmpty()) {
            wrapper.eq("reproduce_rate", reproduceRate);
        }
        if (closeReason != null && !closeReason.isEmpty()) {
            wrapper.eq("close_reason", closeReason);
        }
        wrapper.orderByDesc("create_time");

        IPage<Bug> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        IPage<Bug> result = baseMapper.selectPage(pageParam, wrapper);

        List<Bug> records = result.getRecords();

        // 查询用户信息
        Set<Integer> userIds = new HashSet<>();
        records.forEach(b -> {
            if (b.getReporterId() != null) userIds.add(b.getReporterId());
            if (b.getAssigneeId() != null) userIds.add(b.getAssigneeId());
            if (b.getCreateUserId() != null) userIds.add(b.getCreateUserId());
        });
        Map<Integer, String> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(u -> u.getId().intValue(), User::getUsername, (a, b) -> a));
        }

        // 查询需求标题
        Set<Integer> reqIds = records.stream()
                .map(Bug::getRequirementId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> reqMap = new HashMap<>();
        if (!reqIds.isEmpty()) {
            List<Requirement> reqs = requirementMapper.selectBatchIds(reqIds);
            reqMap = reqs.stream()
                    .collect(Collectors.toMap(Requirement::getId, Requirement::getTitle, (a, b) -> a));
        }

        // 查询用例名称
        Set<Integer> caseIds = records.stream()
                .map(Bug::getTestCaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> caseMap = new HashMap<>();
        if (!caseIds.isEmpty()) {
            List<TestCase> cases = testCaseMapper.selectBatchIds(caseIds);
            caseMap = cases.stream()
                    .collect(Collectors.toMap(TestCase::getId, TestCase::getCaseName, (a, b) -> a));
        }

        // 查询模块名称
        Set<Integer> moduleIds = records.stream()
                .map(Bug::getModuleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> moduleMap = new HashMap<>();
        if (!moduleIds.isEmpty()) {
            List<com.mokatest.platform.demos.qa.domain.QaModule> modules = qaModuleMapper.selectBatchIds(moduleIds);
            moduleMap = modules.stream()
                    .collect(Collectors.toMap(com.mokatest.platform.demos.qa.domain.QaModule::getId, com.mokatest.platform.demos.qa.domain.QaModule::getModuleName, (a, b) -> a));
        }

        // 查询发现计划名称（通过 plan_case_id -> test_plan_case -> test_plan）
        Set<Integer> planCaseIds = records.stream()
                .map(Bug::getPlanCaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> planNameMap = new HashMap<>();
        if (!planCaseIds.isEmpty()) {
            List<com.mokatest.platform.demos.qa.domain.TestPlanCase> planCases = testPlanCaseMapper.selectBatchIds(planCaseIds);
            Set<Integer> planIds = planCases.stream()
                    .map(com.mokatest.platform.demos.qa.domain.TestPlanCase::getPlanId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Integer, Integer> planCaseToPlanMap = planCases.stream()
                    .collect(Collectors.toMap(com.mokatest.platform.demos.qa.domain.TestPlanCase::getId, com.mokatest.platform.demos.qa.domain.TestPlanCase::getPlanId, (a, b) -> a));
            if (!planIds.isEmpty()) {
                List<com.mokatest.platform.demos.qa.domain.TestPlan> plans = testPlanMapper.selectBatchIds(planIds);
                Map<Integer, String> planIdToNameMap = plans.stream()
                        .collect(Collectors.toMap(com.mokatest.platform.demos.qa.domain.TestPlan::getId, com.mokatest.platform.demos.qa.domain.TestPlan::getPlanName, (a, b) -> a));
                for (Bug bug : records) {
                    if (bug.getPlanCaseId() != null) {
                        Integer planId = planCaseToPlanMap.get(bug.getPlanCaseId());
                        if (planId != null) {
                            planNameMap.put(bug.getPlanCaseId(), planIdToNameMap.getOrDefault(planId, ""));
                        }
                    }
                }
            }
        }

        // 组装数据
        List<BugVO> voList = new ArrayList<>();
        for (Bug bug : records) {
            BugVO vo = new BugVO();
            vo.setId(bug.getId());
            vo.setBugCode(bug.getBugCode());
            vo.setTitle(bug.getTitle());
            vo.setDescription(bug.getDescription());
            vo.setReproduceSteps(bug.getReproduceSteps());
            vo.setSeverity(bug.getSeverity());
            vo.setPriority(bug.getPriority());
            vo.setStatus(bug.getStatus());
            vo.setProjectId(bug.getProjectId());
            vo.setRequirementId(bug.getRequirementId());
            vo.setRequirementTitle(reqMap.getOrDefault(bug.getRequirementId(), ""));
            vo.setTestCaseId(bug.getTestCaseId());
            vo.setCaseName(caseMap.getOrDefault(bug.getTestCaseId(), ""));
            vo.setReporterId(bug.getReporterId());
            vo.setReporterName(userMap.getOrDefault(bug.getReporterId(), ""));
            vo.setAssigneeId(bug.getAssigneeId());
            vo.setAssigneeName(userMap.getOrDefault(bug.getAssigneeId(), ""));
            vo.setModuleId(bug.getModuleId());
            vo.setModuleName(moduleMap.getOrDefault(bug.getModuleId(), ""));
            vo.setDeadline(bug.getDeadline());
            vo.setEnvironment(bug.getEnvironment());
            vo.setFoundVersion(bug.getFoundVersion());
            vo.setFixedVersion(bug.getFixedVersion());
            vo.setReproduceRate(bug.getReproduceRate());
            vo.setCloseReason(bug.getCloseReason());
            vo.setTags(bug.getTags());
            vo.setCreateUserId(bug.getCreateUserId());
            vo.setCreateUserName(userMap.getOrDefault(bug.getCreateUserId(), ""));
            vo.setCreateTime(bug.getCreateTime());
            vo.setUpdateTime(bug.getUpdateTime());
            vo.setPlanName(planNameMap.getOrDefault(bug.getPlanCaseId(), ""));
            voList.add(vo);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", voList);
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        return SaResult.ok().setData(data);
    }

    @Override
    public SaResult saveOrUpdateBug(Bug bug) {
        if (bug == null) {
            return SaResult.error("缺少参数");
        }
        if (bug.getProjectId() == null) {
            return SaResult.error("缺少项目ID");
        }
        if (bug.getTitle() == null || bug.getTitle().trim().isEmpty()) {
            return SaResult.error("BUG标题不能为空");
        }

        if (bug.getBugCode() == null || bug.getBugCode().trim().isEmpty()) {
            bug.setBugCode(generateBugCode());
        }
        if (bug.getSeverity() == null) {
            bug.setSeverity("NORMAL");
        }
        if (bug.getPriority() == null) {
            bug.setPriority("MEDIUM");
        }
        if (bug.getStatus() == null) {
            bug.setStatus("NEW");
        }

        Integer loginId = StpUtil.getLoginIdAsInt();
        // 更新时记录变更日志
        Bug oldBug = null;
        if (bug.getId() != null) {
            oldBug = getById(bug.getId());
        }

        if (bug.getId() == null) {
            bug.setReporterId(loginId);
            bug.setCreateUserId(loginId);
            bug.setCreateTime(new Date());
        }
        bug.setUpdateUserId(loginId);
        bug.setUpdateTime(new Date());

        boolean success = saveOrUpdate(bug);

        // 记录操作日志
        if (success && oldBug != null) {
            logBugChanges(oldBug, bug, loginId);
        }

        // 发送指派通知
        if (success) {
            sendBugAssignedMessage(bug, oldBug, loginId);
            // 编辑保存时发送更新通知（与指派场景独立开关，指派人变更时两个场景都可能触发）
            if (oldBug != null) {
                sendBugUpdatedMessage(bug, loginId);
            }
        }

        return success ? SaResult.ok().setData(bug.getId()) : SaResult.error("保存失败");
    }

    /**
     * 发送 BUG 创建/指派通知。
     * 新增 BUG：启用「BUG 创建」场景时发创建通知（默认启用），否则保持原指派通知行为；
     * 更新 BUG：指派人变化时发指派通知。
     */
    private void sendBugAssignedMessage(Bug bug, Bug oldBug, Integer loginId) {
        Integer newAssigneeId = bug.getAssigneeId();
        if (newAssigneeId == null) {
            log.debug("BUG 指派人为空，跳过通知，bugId={}", bug.getId());
            return;
        }
        boolean isCreate = oldBug == null;
        boolean shouldNotify = isCreate
                ? !newAssigneeId.equals(loginId)
                : !newAssigneeId.equals(oldBug.getAssigneeId());
        log.debug("BUG 指派通知判断，bugId={}，newAssigneeId={}，loginId={}，oldAssigneeId={}，shouldNotify={}",
                bug.getId(), newAssigneeId, loginId,
                oldBug != null ? oldBug.getAssigneeId() : null, shouldNotify);
        if (!shouldNotify) {
            return;
        }
        boolean useCreatedEvent = isCreate
                && notifyDispatchService.isScenarioEnabled(bug.getProjectId(), MessageEventType.BUG_CREATED);
        MessageEventType eventType = useCreatedEvent ? MessageEventType.BUG_CREATED : MessageEventType.BUG_ASSIGNED;
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        fillBugNotifyParams(params, bug, loginId);
        Map<String, Object> snapshot = buildBugSnapshot(bug);
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        java.util.Set<Integer> notified = new java.util.HashSet<>();
        notified.add(newAssigneeId);
        contexts.add(MessageContext.builder()
                .eventType(eventType)
                .senderId(loginId)
                .receiverId(newAssigneeId)
                .receiverRole("ASSIGNEE")
                .projectId(bug.getProjectId()).teamId(getTeamIdByProjectId(bug.getProjectId()))
                .bizType("bug")
                .bizId(bug.getId())
                .params(params)
                .snapshot(snapshot)
                .build());
        // 「报告人」角色被项目启用时一并通知（默认关闭，保持原有行为）
        if (bug.getReporterId() != null && !notified.contains(bug.getReporterId())
                && notifyDispatchService.isRoleEnabled(bug.getProjectId(), eventType, "REPORTER")) {
            contexts.add(MessageContext.builder()
                    .eventType(eventType)
                    .senderId(loginId)
                    .receiverId(bug.getReporterId())
                    .receiverRole("REPORTER")
                    .projectId(bug.getProjectId()).teamId(getTeamIdByProjectId(bug.getProjectId()))
                    .bizType("bug")
                    .bizId(bug.getId())
                    .params(params)
                    .snapshot(snapshot)
                    .build());
        }
        messageService.sendBatch(contexts);
    }

    @Override
    @Transactional
    public SaResult deleteBug(Integer id) {
        if (id == null) {
            return SaResult.error("缺少ID");
        }
        Bug bug = getById(id);
        if (bug == null) {
            return SaResult.error("BUG不存在");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        // 发送删除通知（在删除附属数据之前）
        sendBugDeletedMessage(bug, loginId);

        // 1. 删除 BUG 评论
        QueryWrapper<com.mokatest.platform.demos.qa.domain.BugComment> commentWrapper = new QueryWrapper<>();
        commentWrapper.eq("bug_id", id);
        bugCommentMapper.delete(commentWrapper);

        // 2. 删除 BUG 操作日志
        QueryWrapper<com.mokatest.platform.demos.qa.domain.BugOperationLog> logWrapper = new QueryWrapper<>();
        logWrapper.eq("bug_id", id);
        bugOperationLogMapper.delete(logWrapper);

        // 3. 逻辑删除 BUG 本身
        bug.setDeletedAt(new Date());
        boolean success = baseMapper.deleteById(bug) > 0;
        return success ? SaResult.ok("删除成功") : SaResult.error("删除失败");
    }

    @Override
    @Transactional
    public SaResult batchDeleteBug(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return SaResult.error("未选择要删除的BUG");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        // 先查询 BUG 信息并发送删除通知
        List<Bug> bugs = baseMapper.selectBatchIds(ids);
        if (bugs != null) {
            for (Bug bug : bugs) {
                if (bug != null) {
                    sendBugDeletedMessage(bug, loginId);
                }
            }
        }

        // 1. 删除 BUG 评论
        QueryWrapper<com.mokatest.platform.demos.qa.domain.BugComment> commentWrapper = new QueryWrapper<>();
        commentWrapper.in("bug_id", ids);
        bugCommentMapper.delete(commentWrapper);

        // 2. 删除 BUG 操作日志
        QueryWrapper<com.mokatest.platform.demos.qa.domain.BugOperationLog> logWrapper = new QueryWrapper<>();
        logWrapper.in("bug_id", ids);
        bugOperationLogMapper.delete(logWrapper);

        // 3. 批量逻辑删除 BUG（逐条设置 deleted_at）
        Date now = new Date();
        for (Bug bug : bugs) {
            if (bug != null) {
                bug.setDeletedAt(now);
                baseMapper.deleteById(bug);
            }
        }
        return SaResult.ok("批量删除成功");
    }

    @Override
    public SaResult getDetail(Integer id) {
        if (id == null) {
            return SaResult.error("缺少ID");
        }
        Bug bug = getById(id);
        if (bug == null) {
            return SaResult.error("BUG不存在");
        }
        return SaResult.ok().setData(bug);
    }

    @Override
    public SaResult transitionStatus(Integer bugId, String targetStatus) {
        if (bugId == null) {
            return SaResult.error("缺少BUG ID");
        }
        if (targetStatus == null || !VALID_STATUSES.contains(targetStatus)) {
            return SaResult.error("无效的目标状态: " + targetStatus);
        }
        Bug bug = getById(bugId);
        if (bug == null) {
            return SaResult.error("BUG不存在");
        }
        String oldStatus = bug.getStatus();
        Integer loginId = StpUtil.getLoginIdAsInt();
        bug.setStatus(targetStatus);
        bug.setUpdateUserId(loginId);
        bug.setUpdateTime(new Date());
        boolean success = updateById(bug);
        if (success) {
            bugOperationLogService.logOperation(bugId, "status", oldStatus, targetStatus, loginId);
            sendBugStatusChangedMessage(bug, oldStatus, targetStatus, loginId);
        }
        return success ? SaResult.ok("状态已更新为: " + targetStatus) : SaResult.error("状态更新失败");
    }

    /**
     * 发送 BUG 更新通知（编辑保存时触发，报告人/指派人按角色开关过滤）
     */
    private void sendBugUpdatedMessage(Bug bug, Integer loginId) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        fillBugNotifyParams(params, bug, loginId);
        Map<String, Object> bugSnapshot = buildBugSnapshot(bug);
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        if (bug.getReporterId() != null) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.BUG_UPDATED)
                    .senderId(loginId)
                    .receiverId(bug.getReporterId())
                    .receiverRole("REPORTER")
                    .projectId(bug.getProjectId()).teamId(getTeamIdByProjectId(bug.getProjectId()))
                    .bizType("bug")
                    .bizId(bug.getId())
                    .params(params)
                    .snapshot(bugSnapshot)
                    .build());
        }
        if (bug.getAssigneeId() != null && !bug.getAssigneeId().equals(bug.getReporterId())) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.BUG_UPDATED)
                    .senderId(loginId)
                    .receiverId(bug.getAssigneeId())
                    .receiverRole("ASSIGNEE")
                    .projectId(bug.getProjectId()).teamId(getTeamIdByProjectId(bug.getProjectId()))
                    .bizType("bug")
                    .bizId(bug.getId())
                    .params(params)
                    .snapshot(bugSnapshot)
                    .build());
        }
        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    /**
     * 发送 BUG 状态变更通知
     */
    private void sendBugStatusChangedMessage(Bug bug, String oldStatus, String newStatus, Integer loginId) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        fillBugNotifyParams(params, bug, loginId);
        params.put("oldStatus", oldStatus);
        params.put("newStatus", newStatus);
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        Map<String, Object> bugSnapshot = buildBugSnapshot(bug);
        if (bug.getReporterId() != null) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.BUG_STATUS_CHANGED)
                    .senderId(loginId)
                    .receiverId(bug.getReporterId())
                    .receiverRole("REPORTER")
                    .projectId(bug.getProjectId()).teamId(getTeamIdByProjectId(bug.getProjectId()))
                    .bizType("bug")
                    .bizId(bug.getId())
                    .params(params)
                    .snapshot(bugSnapshot)
                    .build());
        }
        if (bug.getAssigneeId() != null && !bug.getAssigneeId().equals(bug.getReporterId())) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.BUG_STATUS_CHANGED)
                    .senderId(loginId)
                    .receiverId(bug.getAssigneeId())
                    .receiverRole("ASSIGNEE")
                    .projectId(bug.getProjectId()).teamId(getTeamIdByProjectId(bug.getProjectId()))
                    .bizType("bug")
                    .bizId(bug.getId())
                    .params(params)
                    .snapshot(bugSnapshot)
                    .build());
        }
        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    /**
     * 发送 BUG 删除通知
     */
    private void sendBugDeletedMessage(Bug bug, Integer loginId) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        fillBugNotifyParams(params, bug, loginId);
        params.put("operateTime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        Map<String, Object> bugSnapshot = buildBugSnapshot(bug);
        if (bug.getReporterId() != null) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.BUG_DELETED)
                    .senderId(loginId)
                    .receiverId(bug.getReporterId())
                    .receiverRole("REPORTER")
                    .projectId(bug.getProjectId()).teamId(getTeamIdByProjectId(bug.getProjectId()))
                    .bizType("bug")
                    .bizId(bug.getId())
                    .params(params)
                    .snapshot(bugSnapshot)
                    .build());
        }
        if (bug.getAssigneeId() != null && !bug.getAssigneeId().equals(bug.getReporterId())) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.BUG_DELETED)
                    .senderId(loginId)
                    .receiverId(bug.getAssigneeId())
                    .receiverRole("ASSIGNEE")
                    .projectId(bug.getProjectId()).teamId(getTeamIdByProjectId(bug.getProjectId()))
                    .bizType("bug")
                    .bizId(bug.getId())
                    .params(params)
                    .snapshot(bugSnapshot)
                    .build());
        }
        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    /**
     * 构建 BUG 快照（用于消息详情展示）
     */
    private Map<String, Object> buildBugSnapshot(Bug bug) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("bugCode", bug.getBugCode());
        snapshot.put("title", bug.getTitle());
        snapshot.put("description", bug.getDescription());
        snapshot.put("severity", bug.getSeverity());
        snapshot.put("priority", bug.getPriority());
        snapshot.put("status", bug.getStatus());
        snapshot.put("environment", bug.getEnvironment());
        snapshot.put("reproduceSteps", bug.getReproduceSteps());
        snapshot.put("foundVersion", bug.getFoundVersion());
        snapshot.put("fixedVersion", bug.getFixedVersion());
        snapshot.put("deadline", bug.getDeadline());
        snapshot.put("reproduceRate", bug.getReproduceRate());
        snapshot.put("closeReason", bug.getCloseReason());
        snapshot.put("moduleId", bug.getModuleId());
        snapshot.put("requirementId", bug.getRequirementId());
        snapshot.put("testCaseId", bug.getTestCaseId());
        snapshot.put("reporterId", bug.getReporterId());
        snapshot.put("assigneeId", bug.getAssigneeId());
        snapshot.put("tags", bug.getTags());
        fillProjectTeamInfo(snapshot, bug.getProjectId());
        return snapshot;
    }

    /**
     * 填充快照中的项目/团队名称
     */
    private void fillProjectTeamInfo(Map<String, Object> snapshot, Integer projectId) {
        if (projectId == null) {
            snapshot.put("projectName", null);
            snapshot.put("teamName", null);
            return;
        }
        com.mokatest.platform.demos.domain.ui.Project project = projectMapper.selectById(projectId);
        String projectName = project != null ? project.getProjectName() : null;
        String teamName = null;
        if (project != null && project.getTeamId() != null) {
            com.mokatest.platform.demos.domain.ui.Team team = teamMapper.selectById(project.getTeamId());
            teamName = team != null ? team.getTeamName() : null;
        }
        snapshot.put("projectName", projectName);
        snapshot.put("teamName", teamName);
    }

    /**
     * 根据项目ID获取团队ID
     */
    private Integer getTeamIdByProjectId(Integer projectId) {
        if (projectId == null) {
            return null;
        }
        com.mokatest.platform.demos.domain.ui.Project project = projectMapper.selectById(projectId);
        return project != null ? project.getTeamId() : null;
    }

    /**
     * 获取操作人名称
     */
    private String getOperatorName(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return String.valueOf(userId);
        }
        return user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername();
    }

    /**
     * 获取项目名称（通知变量用）
     */
    private String getProjectName(Integer projectId) {
        if (projectId == null) {
            return null;
        }
        com.mokatest.platform.demos.domain.ui.Project project = projectMapper.selectById(projectId);
        return project != null ? project.getProjectName() : null;
    }

    /**
     * 获取模块名称（通知变量用）
     */
    private String getModuleName(Integer moduleId) {
        if (moduleId == null) {
            return null;
        }
        com.mokatest.platform.demos.qa.domain.QaModule module = qaModuleMapper.selectById(moduleId);
        return module != null ? module.getModuleName() : null;
    }

    /**
     * 填充 BUG 通知公共变量（白名单内的变量在此统一提供，场景用不到的自动忽略）
     */
    private void fillBugNotifyParams(java.util.Map<String, Object> params, Bug bug, Integer loginId) {
        params.put("bugCode", bug.getBugCode());
        params.put("bugTitle", bug.getTitle());
        params.put("operatorName", getOperatorName(loginId));
        params.put("severity", bug.getSeverity());
        params.put("priority", bug.getPriority());
        params.put("environment", bug.getEnvironment());
        params.put("deadline", bug.getDeadline() != null
                ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(bug.getDeadline()) : null);
        params.put("foundVersion", bug.getFoundVersion());
        params.put("fixedVersion", bug.getFixedVersion());
        params.put("projectName", getProjectName(bug.getProjectId()));
        params.put("moduleName", getModuleName(bug.getModuleId()));
    }

    private void logBugChanges(Bug oldBug, Bug newBug, Integer operatorId) {
        compareAndLog(oldBug.getId(), "title", oldBug.getTitle(), newBug.getTitle(), operatorId);
        compareAndLog(oldBug.getId(), "severity", oldBug.getSeverity(), newBug.getSeverity(), operatorId);
        compareAndLog(oldBug.getId(), "priority", oldBug.getPriority(), newBug.getPriority(), operatorId);
        compareAndLog(oldBug.getId(), "status", oldBug.getStatus(), newBug.getStatus(), operatorId);
        compareAndLog(oldBug.getId(), "assigneeId", oldBug.getAssigneeId(), newBug.getAssigneeId(), operatorId);
        compareAndLog(oldBug.getId(), "moduleId", oldBug.getModuleId(), newBug.getModuleId(), operatorId);
        compareAndLog(oldBug.getId(), "environment", oldBug.getEnvironment(), newBug.getEnvironment(), operatorId);
        compareAndLog(oldBug.getId(), "deadline", oldBug.getDeadline(), newBug.getDeadline(), operatorId);
        compareAndLog(oldBug.getId(), "fixedVersion", oldBug.getFixedVersion(), newBug.getFixedVersion(), operatorId);
        compareAndLog(oldBug.getId(), "closeReason", oldBug.getCloseReason(), newBug.getCloseReason(), operatorId);
    }

    private void compareAndLog(Integer bugId, String fieldName, Object oldValue, Object newValue, Integer operatorId) {
        String oldStr = oldValue != null ? oldValue.toString() : null;
        String newStr = newValue != null ? newValue.toString() : null;
        if (!Objects.equals(oldStr, newStr)) {
            bugOperationLogService.logOperation(bugId, fieldName, oldStr, newStr, operatorId);
        }
    }

    private static final java.util.concurrent.ThreadLocalRandom BUG_RANDOM = java.util.concurrent.ThreadLocalRandom.current();
    private static final java.util.concurrent.atomic.AtomicInteger BUG_SEQ = new java.util.concurrent.atomic.AtomicInteger(0);

    private String generateBugCode() {
        String dateStr = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
        int seq = BUG_SEQ.incrementAndGet() % 10000;
        int random = BUG_RANDOM.nextInt(1000, 9999);
        return "BUG-" + dateStr + "-" + String.format("%04d%04d", seq, random);
    }

    @Override
    public SaResult stats(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("total", baseMapper.selectCount(new QueryWrapper<Bug>().eq("project_id", projectId)));
        map.put("open", baseMapper.selectCount(new QueryWrapper<Bug>().eq("project_id", projectId)
                .notIn("status", "CLOSED", "REJECTED")));
        map.put("serious", baseMapper.selectCount(new QueryWrapper<Bug>().eq("project_id", projectId).eq("severity", "SERIOUS")));
        map.put("fatal", baseMapper.selectCount(new QueryWrapper<Bug>().eq("project_id", projectId).eq("severity", "FATAL")));
        return SaResult.data(map);
    }
}
