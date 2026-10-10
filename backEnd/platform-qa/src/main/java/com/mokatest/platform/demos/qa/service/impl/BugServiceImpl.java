package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import com.mokatest.platform.demos.mapper.TeamMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.qa.common.ProjectEntityGuard;
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
    private final com.mokatest.platform.demos.qa.mapper.BugCaseMapper bugCaseMapper;
    private final MessageService messageService;
    private final com.mokatest.platform.demos.qa.message.service.impl.NotifyDispatchService notifyDispatchService;

    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            "NEW", "CONFIRMED", "FIXING", "FIXED", "VERIFIED", "CLOSED", "REJECTED"
    ));

    @Override
    public SaResult listByProject(Integer projectId, String keyword, String status, String severity, String priority, Integer requirementId, Integer testCaseId, Integer moduleId, String environment, String reproduceRate, String closeReason, Integer requirementScopeId, Integer page, Integer pageSize) {
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
        if (requirementScopeId != null) {
            // 需求合并口径：requirement_id = X 的 BUG ∪ 需求下用例关联的 BUG
            // 用例归属需求按 requirement_id（主）+ requirement_ids（多需求逗号串）双口径
            QueryWrapper<TestCase> caseW = new QueryWrapper<>();
            caseW.eq("project_id", projectId)
                    .and(w -> w.eq("requirement_id", requirementScopeId)
                            .or().apply("FIND_IN_SET({0}, requirement_ids)", requirementScopeId));
            List<Integer> caseIds = testCaseMapper.selectList(caseW).stream()
                    .map(TestCase::getId)
                    .collect(Collectors.toList());
            List<Integer> linkedBugIds = new ArrayList<>();
            if (!caseIds.isEmpty()) {
                QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> bcW = new QueryWrapper<>();
                bcW.in("test_case_id", caseIds);
                linkedBugIds = bugCaseMapper.selectList(bcW).stream()
                        .map(com.mokatest.platform.demos.qa.domain.BugCase::getBugId)
                        .collect(Collectors.toList());
            }
            final List<Integer> finalLinkedBugIds = linkedBugIds;
            wrapper.and(w -> {
                w.eq("requirement_id", requirementScopeId);
                if (!finalLinkedBugIds.isEmpty()) {
                    w.or().in("id", finalLinkedBugIds);
                }
            });
        } else if (requirementId != null) {
            wrapper.eq("requirement_id", requirementId);
        }
        if (testCaseId != null) {
            // 只查 bug_case 关联表
            QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> bcWrapper = new QueryWrapper<>();
            bcWrapper.eq("test_case_id", testCaseId);
            List<Integer> linkedBugIds = bugCaseMapper.selectList(bcWrapper).stream()
                    .map(com.mokatest.platform.demos.qa.domain.BugCase::getBugId)
                    .collect(java.util.stream.Collectors.toList());
            if (linkedBugIds.isEmpty()) {
                wrapper.eq("id", -1);
            } else {
                wrapper.in("id", linkedBugIds);
            }
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
            vo.setPlanCaseId(bug.getPlanCaseId());
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
    @Transactional
    public SaResult saveOrUpdateBug(Bug bug) {
        if (bug == null) {
            return SaResult.error("缺少参数");
        }
        Integer ctxProjectId = ProjectContextHolder.getProjectId();
        if (ctxProjectId == null) {
            return SaResult.error("缺少项目上下文（X-Project-Id）");
        }
        if (bug.getTitle() == null || bug.getTitle().trim().isEmpty()) {
            return SaResult.error("BUG标题不能为空");
        }

        if (bug.getId() == null && (bug.getBugCode() == null || bug.getBugCode().trim().isEmpty())) {
            // 先占一个临时唯一值，等插入拿到自增 id 后再改成 BUG-{id}
            bug.setBugCode("TMP-" + System.nanoTime());
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
        // 更新时记录变更日志；更新分支校验归属项目（IDOR）并以老 BUG projectId 覆盖入参，
        // 新增分支以当前项目上下文覆盖，杜绝前端篡改 projectId 跨项目写入
        Bug oldBug = null;
        if (bug.getId() != null) {
            oldBug = ProjectEntityGuard.requireInProject(getById(bug.getId()));
            bug.setProjectId(oldBug.getProjectId());
        } else {
            bug.setProjectId(ctxProjectId);
        }

        if (bug.getId() == null) {
            bug.setReporterId(loginId);
            bug.setCreateUserId(loginId);
            bug.setCreateTime(new Date());
        }
        bug.setUpdateUserId(loginId);
        bug.setUpdateTime(new Date());

        boolean success = saveOrUpdate(bug);
        if (!success) {
            return SaResult.error("保存失败");
        }
        // 新增BUG时，把临时编号改为短格式 BUG-{id}
        if (bug.getBugCode() != null && bug.getBugCode().startsWith("TMP-")) {
            bug.setBugCode("BUG-" + bug.getId());
            updateById(bug);
        }
        if (oldBug != null) {
            logBugChanges(oldBug, bug, loginId);
        }

        // 发送指派通知
        sendBugAssignedMessage(bug, oldBug, loginId);
        if (oldBug != null) {
            sendBugUpdatedMessage(bug, loginId);
        }

        return SaResult.ok().setData(bug.getId());
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
        Bug bug = ProjectEntityGuard.requireInProject(getById(id));
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

        // 3. 逻辑删除 BUG 本身（先改 bug_code 避免唯一约束冲突）
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Bug> codeWrapper = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        codeWrapper.eq("id", id).set("bug_code", bug.getBugCode() + "-DEL-" + id);
        baseMapper.update(null, codeWrapper);
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
        // 先查询 BUG 信息并逐条校验归属项目（IDOR）后发送删除通知
        List<Bug> bugs = baseMapper.selectBatchIds(ids);
        if (bugs != null) {
            for (Bug bug : bugs) {
                if (bug != null) {
                    ProjectEntityGuard.requireInProject(bug);
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

        // 3. 批量逻辑删除 BUG（逐条改 bug_code 避免唯一约束冲突）
        Date now = new Date();
        for (Bug bug : bugs) {
            if (bug != null) {
                com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Bug> codeWrapper = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
                codeWrapper.eq("id", bug.getId()).set("bug_code", bug.getBugCode() + "-DEL-" + bug.getId());
                baseMapper.update(null, codeWrapper);
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
        Bug bug = ProjectEntityGuard.requireInProject(getById(id));
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
        Bug bug = ProjectEntityGuard.requireInProject(getById(bugId));
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
    @Transactional(rollbackFor = Exception.class)
    public SaResult generateRegressionCase(Integer bugId, Integer folderId) {
        if (bugId == null) {
            return SaResult.error("缺少BUG ID");
        }
        Bug bug = ProjectEntityGuard.requireInProject(getById(bugId));
        // 仅已修复/已验证/已关闭的 BUG 允许沉淀回归用例
        if (!Arrays.asList("FIXED", "VERIFIED", "CLOSED").contains(bug.getStatus())) {
            return SaResult.error("仅已修复/已验证/已关闭的BUG才能沉淀回归用例");
        }
        if (bug.getRegressionCaseId() != null) {
            return SaResult.error("该BUG已沉淀回归用例（用例ID: " + bug.getRegressionCaseId() + "）");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        Date now = new Date();

        TestCase testCase = new TestCase();
        testCase.setProjectId(bug.getProjectId());
        testCase.setCaseName("[回归] " + bug.getTitle());
        // 前置条件：环境描述
        String envDesc = bug.getEnvironment() == null ? "" :
                Map.of("TEST", "测试环境", "STAGING", "预发环境", "PROD", "生产环境")
                        .getOrDefault(bug.getEnvironment(), bug.getEnvironment());
        testCase.setPreCondition(envDesc.isEmpty() ? null : "环境：" + envDesc
                + (bug.getFoundVersion() != null ? "；发现版本：" + bug.getFoundVersion() : ""));
        // 测试步骤：若 BUG 已关联用例，先复制该用例步骤作为基础，再叠加重现步骤
        List<TestCase.TestStepItem> steps = new ArrayList<>();
        // 从 bug_case 关联表查第一条关联用例
        QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> bcStepW = new QueryWrapper<>();
        bcStepW.eq("bug_id", bug.getId()).last("LIMIT 1");
        com.mokatest.platform.demos.qa.domain.BugCase linkedBc = bugCaseMapper.selectOne(bcStepW);
        if (linkedBc != null) {
            TestCase baseCase = testCaseMapper.selectById(linkedBc.getTestCaseId());
            if (baseCase != null && baseCase.getTestSteps() != null) {
                steps.addAll(baseCase.getTestSteps());
            }
        }
        if (bug.getReproduceSteps() != null && !bug.getReproduceSteps().isBlank()) {
            for (String line : bug.getReproduceSteps().split("\\R")) {
                String stepText = line.trim();
                if (stepText.isEmpty()) continue;
                TestCase.TestStepItem item = new TestCase.TestStepItem();
                item.setStep(stepText);
                item.setExpected("功能正常，不再复现原BUG");
                steps.add(item);
            }
        }
        if (steps.isEmpty()) {
            TestCase.TestStepItem item = new TestCase.TestStepItem();
            item.setStep("按原BUG场景执行回归验证");
            item.setExpected("功能正常，不再复现原BUG");
            steps.add(item);
        }
        testCase.setTestSteps(steps);
        // 严重程度 → 优先级映射：FATAL/SERIOUS→P0, NORMAL→P1, TIPS→P2
        String priority = switch (bug.getSeverity() == null ? "NORMAL" : bug.getSeverity()) {
            case "FATAL", "SERIOUS" -> "P0";
            case "TIPS" -> "P2";
            default -> "P1";
        };
        testCase.setPriority(priority);
        testCase.setCaseType("FUNCTION");
        testCase.setStatus("DRAFT");
        testCase.setModuleId(bug.getModuleId());
        testCase.setRequirementId(bug.getRequirementId());
        // 标签追加「回归」
        String tags = bug.getTags() == null || bug.getTags().isBlank() ? "回归"
                : (bug.getTags().contains("回归") ? bug.getTags() : bug.getTags() + ",回归");
        testCase.setTags(tags);
        testCase.setCaseCode("TMP-" + System.nanoTime());
        testCase.setCreateUserId(loginId);
        testCase.setUpdateUserId(loginId);
        testCase.setCreateTime(now);
        testCase.setUpdateTime(now);
        testCaseMapper.insert(testCase);
        // 临时编号改为短格式 CASE-{id}
        testCase.setCaseCode("CASE-" + testCase.getId());
        testCaseMapper.updateById(testCase);
        // 指定文件夹时设置 folder_id（TestCase 实体未映射该字段，直接用 UpdateWrapper 写列）
        if (folderId != null && folderId > 0) {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TestCase> folderWrapper =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            folderWrapper.eq("id", testCase.getId()).set("folder_id", folderId);
            testCaseMapper.update(null, folderWrapper);
        }
        // 回填 BUG 的回归用例ID
        Bug update = new Bug();
        update.setId(bug.getId());
        update.setRegressionCaseId(testCase.getId());
        updateById(update);
        // 写 BUG 操作日志
        bugOperationLogService.logOperation(bugId, "regressionCaseId", null,
                testCase.getCaseCode(), loginId);
        return SaResult.ok("回归用例已生成").setData(testCase.getId());
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

    @Override
    public SaResult bindTestCase(Integer bugId, Integer testCaseId) {
        if (bugId == null) {
            return SaResult.error("缺少BUG ID");
        }
        // 数据边界：BUG 须归属当前项目（IDOR）
        ProjectEntityGuard.requireInProject(getById(bugId));
        if (testCaseId == null) {
            // 解绑：删除 bug_case 中该 BUG 的所有关联
            QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> delW = new QueryWrapper<>();
            delW.eq("bug_id", bugId);
            boolean success = bugCaseMapper.delete(delW) > 0;
            return success ? SaResult.ok("已解绑用例") : SaResult.ok("无关联记录");
        }
        // 关联：用例须归属当前项目（双向校验），插入 bug_case（避免重复）
        ProjectEntityGuard.requireInProject(testCaseMapper.selectById(testCaseId));
        QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> existW = new QueryWrapper<>();
        existW.eq("bug_id", bugId).eq("test_case_id", testCaseId);
        if (bugCaseMapper.selectCount(existW) == 0) {
            bugCaseMapper.insert(new com.mokatest.platform.demos.qa.domain.BugCase(null, bugId, testCaseId));
        }
        return SaResult.ok("已关联用例");
    }

    @Override
    @Transactional
    public SaResult bindCases(Integer bugId, List<Integer> testCaseIds) {
        if (bugId == null) return SaResult.error("缺少BUG ID");
        if (testCaseIds == null || testCaseIds.isEmpty()) return SaResult.error("未选择用例");
        // 数据边界：BUG 与待关联用例均须归属当前项目（双向校验，IDOR）
        ProjectEntityGuard.requireInProject(getById(bugId));
        for (Integer tcId : testCaseIds) {
            ProjectEntityGuard.requireInProject(testCaseMapper.selectById(tcId));
        }
        // 先查出已有的关联，避免重复插入
        QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> existWrapper = new QueryWrapper<>();
        existWrapper.eq("bug_id", bugId);
        Set<Integer> existingIds = bugCaseMapper.selectList(existWrapper).stream()
                .map(com.mokatest.platform.demos.qa.domain.BugCase::getTestCaseId)
                .collect(java.util.stream.Collectors.toSet());
        List<com.mokatest.platform.demos.qa.domain.BugCase> toInsert = new ArrayList<>();
        for (Integer tcId : testCaseIds) {
            if (!existingIds.contains(tcId)) {
                toInsert.add(new com.mokatest.platform.demos.qa.domain.BugCase(null, bugId, tcId));
            }
        }
        if (!toInsert.isEmpty()) {
            for (com.mokatest.platform.demos.qa.domain.BugCase bc : toInsert) {
                bugCaseMapper.insert(bc);
            }
        }
        return SaResult.ok("已关联 " + toInsert.size() + " 条用例");
    }

    @Override
    @Transactional
    public SaResult unbindCase(Integer bugId, Integer testCaseId) {
        if (bugId == null || testCaseId == null) return SaResult.error("缺少参数");
        // 数据边界：BUG 与用例均须归属当前项目（双向校验，IDOR）
        ProjectEntityGuard.requireInProject(getById(bugId));
        ProjectEntityGuard.requireInProject(testCaseMapper.selectById(testCaseId));
        QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> wrapper = new QueryWrapper<>();
        wrapper.eq("bug_id", bugId).eq("test_case_id", testCaseId);
        bugCaseMapper.delete(wrapper);
        return SaResult.ok("已解绑");
    }

    @Override
    @Transactional
    public SaResult bindPlanCase(Integer bugId, Integer planCaseId) {
        if (bugId == null) return SaResult.error("缺少BUG ID");
        Bug bug = ProjectEntityGuard.requireInProject(getById(bugId));
        if (planCaseId != null) {
            // 数据边界：计划用例必须存在且与 BUG 同项目，禁止跨项目绑定
            com.mokatest.platform.demos.qa.domain.TestPlanCase pc = testPlanCaseMapper.selectById(planCaseId);
            if (pc == null) return SaResult.error("计划用例不存在");
            com.mokatest.platform.demos.qa.domain.TestPlan plan = testPlanMapper.selectById(pc.getPlanId());
            if (plan == null || !plan.getProjectId().equals(bug.getProjectId())) {
                return SaResult.error("计划用例与BUG不属于同一项目");
            }
        }
        // planCaseId 允许为 null（解绑），必须用 UpdateWrapper.set 才能生成 SET NULL
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Bug> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("id", bugId).set("plan_case_id", planCaseId);
        baseMapper.update(null, wrapper);
        return SaResult.ok(planCaseId == null ? "已取消关联" : "已关联");
    }

    @Override
    public SaResult listPlanCaseBugs(Integer testCaseId) {
        if (testCaseId == null) return SaResult.error("缺少用例ID");
        // 数据边界：用例须归属当前项目（IDOR）
        ProjectEntityGuard.requireInProject(testCaseMapper.selectById(testCaseId));
        // test_case_id -> test_plan_case.id -> bug.plan_case_id
        QueryWrapper<com.mokatest.platform.demos.qa.domain.TestPlanCase> pcW = new QueryWrapper<>();
        pcW.eq("test_case_id", testCaseId);
        List<Integer> pcIds = testPlanCaseMapper.selectList(pcW).stream()
                .map(com.mokatest.platform.demos.qa.domain.TestPlanCase::getId)
                .collect(java.util.stream.Collectors.toList());
        if (pcIds.isEmpty()) return SaResult.ok().setData(Collections.emptyList());
        QueryWrapper<Bug> w = new QueryWrapper<>();
        w.in("plan_case_id", pcIds).orderByDesc("create_time");
        return SaResult.ok().setData(baseMapper.selectList(w));
    }

    @Override
    public SaResult listLinkedCases(Integer bugId) {
        if (bugId == null) return SaResult.error("缺少BUG ID");
        // 数据边界：BUG 须归属当前项目（IDOR）
        ProjectEntityGuard.requireInProject(getById(bugId));
        QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> wrapper = new QueryWrapper<>();
        wrapper.eq("bug_id", bugId);
        List<com.mokatest.platform.demos.qa.domain.BugCase> links = bugCaseMapper.selectList(wrapper);
        if (links.isEmpty()) return SaResult.ok().setData(Collections.emptyList());
        List<Integer> caseIds = links.stream()
                .map(com.mokatest.platform.demos.qa.domain.BugCase::getTestCaseId)
                .collect(java.util.stream.Collectors.toList());
        List<TestCase> cases = testCaseMapper.selectBatchIds(caseIds);
        return SaResult.ok().setData(cases);
    }
}
