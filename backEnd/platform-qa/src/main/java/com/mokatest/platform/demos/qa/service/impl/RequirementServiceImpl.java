package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import com.mokatest.platform.demos.mapper.TeamMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.vo.RequirementVO;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import com.mokatest.platform.demos.qa.message.dto.MessageContext;
import com.mokatest.platform.demos.qa.message.enums.MessageEventType;
import com.mokatest.platform.demos.qa.message.service.MessageService;
import com.mokatest.platform.demos.qa.service.RequirementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * 需求池 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementServiceImpl extends ServiceImpl<RequirementMapper, Requirement> implements RequirementService {

    private final UserMapper userMapper;
    private final TestCaseMapper testCaseMapper;
    private final ProjectMapper projectMapper;
    private final TeamMapper teamMapper;
    private final com.mokatest.platform.demos.qa.mapper.QaModuleMapper qaModuleMapper;
    private final com.mokatest.platform.demos.qa.mapper.BugMapper bugMapper;
    private final MessageService messageService;
    private final com.mokatest.platform.demos.qa.message.service.impl.NotifyDispatchService notifyDispatchService;

    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            "DRAFT", "REVIEWING", "CONFIRMED", "DEVELOPING", "TESTING", "RELEASED", "CLOSED"
    ));

    @Override
    public SaResult listByProject(Integer projectId, String keyword, String status, Integer moduleId, String reqType, String source, Integer page, Integer pageSize) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        QueryWrapper<Requirement> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("title", keyword).or().like("req_code", keyword));
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        if (moduleId != null) {
            wrapper.eq("module_id", moduleId);
        }
        if (reqType != null && !reqType.isEmpty()) {
            wrapper.eq("req_type", reqType);
        }
        if (source != null && !source.isEmpty()) {
            wrapper.eq("source", source);
        }
        wrapper.orderByDesc("create_time");

        IPage<Requirement> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        IPage<Requirement> result = baseMapper.selectPage(pageParam, wrapper);

        List<Requirement> records = result.getRecords();

        // 查询负责人名称
        Set<Integer> userIds = records.stream()
                .map(Requirement::getOwnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(u -> u.getId().intValue(), User::getUsername, (a, b) -> a));
        }

        // 查询模块名称
        Set<Integer> moduleIds = records.stream()
                .map(Requirement::getModuleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> moduleMap = new HashMap<>();
        if (!moduleIds.isEmpty()) {
            List<com.mokatest.platform.demos.qa.domain.QaModule> modules = qaModuleMapper.selectBatchIds(moduleIds);
            moduleMap = modules.stream()
                    .collect(Collectors.toMap(com.mokatest.platform.demos.qa.domain.QaModule::getId, com.mokatest.platform.demos.qa.domain.QaModule::getModuleName, (a, b) -> a));
        }

        // 查询关联用例数
        List<Integer> reqIds = records.stream().map(Requirement::getId).collect(Collectors.toList());
        Map<Integer, Long> caseCountMap = new HashMap<>();
        if (!reqIds.isEmpty()) {
            QueryWrapper<TestCase> caseWrapper = new QueryWrapper<>();
            caseWrapper.in("requirement_id", reqIds);
            List<TestCase> cases = testCaseMapper.selectList(caseWrapper);
            caseCountMap = cases.stream()
                    .collect(Collectors.groupingBy(TestCase::getRequirementId, Collectors.counting()));
        }

        // 查询关联BUG数 & 未关闭BUG数
        Map<Integer, Long> bugCountMap = new HashMap<>();
        Map<Integer, Long> openBugCountMap = new HashMap<>();
        if (!reqIds.isEmpty()) {
            QueryWrapper<com.mokatest.platform.demos.qa.domain.Bug> bugWrapper = new QueryWrapper<>();
            bugWrapper.in("requirement_id", reqIds);
            List<com.mokatest.platform.demos.qa.domain.Bug> bugs = bugMapper.selectList(bugWrapper);
            for (com.mokatest.platform.demos.qa.domain.Bug bug : bugs) {
                Integer reqId = bug.getRequirementId();
                bugCountMap.merge(reqId, 1L, Long::sum);
                if (!"CLOSED".equals(bug.getStatus()) && !"REJECTED".equals(bug.getStatus())) {
                    openBugCountMap.merge(reqId, 1L, Long::sum);
                }
            }
        }

        // 组装数据
        List<RequirementVO> voList = new ArrayList<>();
        for (Requirement req : records) {
            RequirementVO vo = new RequirementVO();
            vo.setId(req.getId());
            vo.setReqCode(req.getReqCode());
            vo.setTitle(req.getTitle());
            vo.setDescription(req.getDescription());
            vo.setPriority(req.getPriority());
            vo.setStatus(req.getStatus());
            vo.setProjectId(req.getProjectId());
            vo.setVersion(req.getVersion());
            vo.setOwnerId(req.getOwnerId());
            vo.setOwnerName(userMap.getOrDefault(req.getOwnerId(), ""));
            vo.setCaseCount(caseCountMap.getOrDefault(req.getId(), 0L));
            vo.setBugCount(bugCountMap.getOrDefault(req.getId(), 0L));
            vo.setOpenBugCount(openBugCountMap.getOrDefault(req.getId(), 0L));
            vo.setModuleId(req.getModuleId());
            vo.setModuleName(moduleMap.getOrDefault(req.getModuleId(), ""));
            vo.setParentId((req.getParentId() != null && req.getParentId() == 0) ? null : req.getParentId());
            vo.setReqType(req.getReqType());
            vo.setSource(req.getSource());
            vo.setParticipants(req.getParticipants());
            vo.setExpectReleaseTime(req.getExpectReleaseTime());
            vo.setTags(req.getTags());
            vo.setCreateUserId(req.getCreateUserId());
            vo.setCreateTime(req.getCreateTime());
            vo.setUpdateTime(req.getUpdateTime());
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
    public SaResult saveOrUpdateRequirement(Requirement requirement) {
        if (requirement == null) {
            return SaResult.error("缺少参数");
        }
        if (requirement.getProjectId() == null) {
            return SaResult.error("缺少项目ID");
        }
        if (requirement.getTitle() == null || requirement.getTitle().trim().isEmpty()) {
            return SaResult.error("需求标题不能为空");
        }

        // 生成编号
        if (requirement.getReqCode() == null || requirement.getReqCode().trim().isEmpty()) {
            requirement.setReqCode(generateReqCode());
        }

        // 默认值
        if (requirement.getPriority() == null) {
            requirement.setPriority("P2");
        }
        if (requirement.getStatus() == null) {
            requirement.setStatus("DRAFT");
        }

        Integer loginId = StpUtil.getLoginIdAsInt();

        // 更新时记录原负责人
        Requirement oldRequirement = null;
        if (requirement.getId() != null) {
            oldRequirement = getById(requirement.getId());
        }

        if (requirement.getId() == null) {
            // 新增
            requirement.setCreateUserId(loginId);
            requirement.setCreateTime(new Date());
        }
        requirement.setUpdateUserId(loginId);
        requirement.setUpdateTime(new Date());

        boolean success = saveOrUpdate(requirement);

        // 发送指派通知
        if (success) {
            sendRequirementAssignedMessage(requirement, oldRequirement, loginId);
            // 编辑保存时发送更新通知（与指派场景独立开关）
            if (oldRequirement != null) {
                sendRequirementUpdatedMessage(requirement, loginId);
            }
        }

        return success ? SaResult.ok().setData(requirement.getId()) : SaResult.error("保存失败");
    }

    /**
     * 发送需求创建/指派通知。
     * 新增需求：启用「需求创建」场景时发创建通知（默认启用），否则保持原指派通知行为；
     * 更新需求：负责人变化时发指派通知。
     */
    private void sendRequirementAssignedMessage(Requirement requirement, Requirement oldRequirement, Integer loginId) {
        Integer newOwnerId = requirement.getOwnerId();
        if (newOwnerId == null) {
            return;
        }
        boolean isCreate = oldRequirement == null;
        boolean shouldNotify = isCreate
                ? !newOwnerId.equals(loginId)
                : !newOwnerId.equals(oldRequirement.getOwnerId());
        if (!shouldNotify) {
            return;
        }
        boolean useCreatedEvent = isCreate
                && notifyDispatchService.isScenarioEnabled(requirement.getProjectId(), MessageEventType.REQ_CREATED);
        MessageEventType eventType = useCreatedEvent ? MessageEventType.REQ_CREATED : MessageEventType.REQ_ASSIGNED;
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("reqCode", requirement.getReqCode());
        params.put("reqTitle", requirement.getTitle());
        params.put("operatorName", getOperatorName(loginId));
        params.put("priority", requirement.getPriority());
        params.put("projectName", getProjectName(requirement.getProjectId()));
        params.put("moduleName", getModuleName(requirement.getModuleId()));
        Map<String, Object> snapshot = buildRequirementSnapshot(requirement);
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        java.util.Set<Integer> notified = new java.util.HashSet<>();
        notified.add(newOwnerId);
        contexts.add(MessageContext.builder()
                .eventType(eventType)
                .senderId(loginId)
                .receiverId(newOwnerId)
                .receiverRole("OWNER")
                .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                .bizType("requirement")
                .bizId(requirement.getId())
                .params(params)
                .snapshot(snapshot)
                .build());
        // 参与人 / 创建人角色被项目启用时一并通知（默认关闭，保持原有行为）
        if (requirement.getParticipants() != null && !requirement.getParticipants().isEmpty()
                && notifyDispatchService.isRoleEnabled(requirement.getProjectId(), eventType, "PARTICIPANT")) {
            try {
                java.util.List<Integer> participantIds = cn.hutool.json.JSONUtil.toList(
                        cn.hutool.json.JSONUtil.parseArray(requirement.getParticipants()), Integer.class);
                for (Integer pid : participantIds) {
                    if (pid != null && !notified.contains(pid)) {
                        notified.add(pid);
                        contexts.add(MessageContext.builder()
                                .eventType(eventType)
                                .senderId(loginId)
                                .receiverId(pid)
                                .receiverRole("PARTICIPANT")
                                .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                                .bizType("requirement")
                                .bizId(requirement.getId())
                                .params(params)
                                .snapshot(snapshot)
                                .build());
                    }
                }
            } catch (Exception e) {
                log.warn("需求参与人解析失败，跳过参与人通知，requirementId={}，participants={}", requirement.getId(), requirement.getParticipants(), e);
            }
        }
        if (requirement.getCreateUserId() != null && !notified.contains(requirement.getCreateUserId())
                && notifyDispatchService.isRoleEnabled(requirement.getProjectId(), eventType, "CREATOR")) {
            contexts.add(MessageContext.builder()
                    .eventType(eventType)
                    .senderId(loginId)
                    .receiverId(requirement.getCreateUserId())
                    .receiverRole("CREATOR")
                    .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .params(params)
                    .snapshot(snapshot)
                    .build());
        }
        messageService.sendBatch(contexts);
    }

    @Override
    @Transactional
    public SaResult deleteRequirement(Integer id) {
        if (id == null) {
            return SaResult.error("缺少ID");
        }
        Requirement requirement = getById(id);
        if (requirement == null) {
            return SaResult.error("需求不存在");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        // 发送删除通知（在解绑关联数据之前）
        sendRequirementDeletedMessage(requirement, loginId);

        // 1. 解绑关联用例的 requirement_id
        UpdateWrapper<TestCase> caseUpdateWrapper = new UpdateWrapper<>();
        caseUpdateWrapper.eq("requirement_id", id).set("requirement_id", null);
        testCaseMapper.update(null, caseUpdateWrapper);

        // 2. 解绑关联 BUG 的 requirement_id
        UpdateWrapper<com.mokatest.platform.demos.qa.domain.Bug> bugUpdateWrapper = new UpdateWrapper<>();
        bugUpdateWrapper.eq("requirement_id", id).set("requirement_id", null);
        bugMapper.update(null, bugUpdateWrapper);

        // 3. 子需求变为根需求（parent_id 置为 0）
        QueryWrapper<Requirement> childWrapper = new QueryWrapper<>();
        childWrapper.eq("parent_id", id);
        Requirement childUpdate = new Requirement();
        childUpdate.setParentId(0);
        baseMapper.update(childUpdate, childWrapper);

        // 4. 逻辑删除需求本身
        requirement.setDeletedAt(new Date());
        boolean success = baseMapper.deleteById(requirement) > 0;
        return success ? SaResult.ok("删除成功") : SaResult.error("删除失败");
    }

    @Override
    @Transactional
    public SaResult batchDeleteRequirement(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return SaResult.error("未选择要删除的需求");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        // 先查询需求信息并发送删除通知
        List<Requirement> requirements = baseMapper.selectBatchIds(ids);
        if (requirements != null) {
            for (Requirement req : requirements) {
                if (req != null) {
                    sendRequirementDeletedMessage(req, loginId);
                }
            }
        }

        // 1. 解绑关联用例
        UpdateWrapper<TestCase> caseUpdateWrapper = new UpdateWrapper<>();
        caseUpdateWrapper.in("requirement_id", ids).set("requirement_id", null);
        testCaseMapper.update(null, caseUpdateWrapper);

        // 2. 解绑关联 BUG
        UpdateWrapper<com.mokatest.platform.demos.qa.domain.Bug> bugUpdateWrapper = new UpdateWrapper<>();
        bugUpdateWrapper.in("requirement_id", ids).set("requirement_id", null);
        bugMapper.update(null, bugUpdateWrapper);

        // 3. 子需求变为根需求
        QueryWrapper<Requirement> childWrapper = new QueryWrapper<>();
        childWrapper.in("parent_id", ids);
        Requirement childUpdate = new Requirement();
        childUpdate.setParentId(0);
        baseMapper.update(childUpdate, childWrapper);

        // 4. 批量逻辑删除需求（逐条设置 deleted_at）
        Date now = new Date();
        for (Requirement req : requirements) {
            if (req != null) {
                req.setDeletedAt(now);
                baseMapper.deleteById(req);
            }
        }
        return SaResult.ok("批量删除成功");
    }

    @Override
    public SaResult getDetail(Integer id) {
        if (id == null) {
            return SaResult.error("缺少ID");
        }
        Requirement requirement = getById(id);
        if (requirement == null) {
            return SaResult.error("需求不存在");
        }
        // parent_id 为 0 表示无父需求，前端统一用 null 展示
        if (requirement.getParentId() != null && requirement.getParentId() == 0) {
            requirement.setParentId(null);
        }
        return SaResult.ok().setData(requirement);
    }

    @Override
    public SaResult transitionStatus(Integer requirementId, String targetStatus) {
        if (requirementId == null) {
            return SaResult.error("缺少需求ID");
        }
        if (targetStatus == null || !VALID_STATUSES.contains(targetStatus)) {
            return SaResult.error("无效的目标状态: " + targetStatus);
        }
        Requirement requirement = getById(requirementId);
        if (requirement == null) {
            return SaResult.error("需求不存在");
        }
        String oldStatus = requirement.getStatus();
        Integer loginId = StpUtil.getLoginIdAsInt();
        requirement.setStatus(targetStatus);
        requirement.setUpdateUserId(loginId);
        requirement.setUpdateTime(new Date());
        boolean success = updateById(requirement);
        if (success) {
            sendRequirementStatusChangedMessage(requirement, oldStatus, targetStatus, loginId);
        }
        return success ? SaResult.ok("状态已更新为: " + targetStatus) : SaResult.error("状态更新失败");
    }

    /**
     * 发送需求更新通知（编辑保存时触发，负责人/参与人/创建人按角色开关过滤）
     */
    private void sendRequirementUpdatedMessage(Requirement requirement, Integer loginId) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("reqCode", requirement.getReqCode());
        params.put("reqTitle", requirement.getTitle());
        params.put("operatorName", getOperatorName(loginId));
        params.put("priority", requirement.getPriority());
        params.put("projectName", getProjectName(requirement.getProjectId()));
        params.put("moduleName", getModuleName(requirement.getModuleId()));
        Map<String, Object> requirementSnapshot = buildRequirementSnapshot(requirement);
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        java.util.Set<Integer> notified = new java.util.HashSet<>();

        if (requirement.getOwnerId() != null) {
            notified.add(requirement.getOwnerId());
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.REQ_UPDATED)
                    .senderId(loginId)
                    .receiverId(requirement.getOwnerId())
                    .receiverRole("OWNER")
                    .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .params(params)
                    .snapshot(requirementSnapshot)
                    .build());
        }
        if (requirement.getParticipants() != null && !requirement.getParticipants().isEmpty()) {
            try {
                java.util.List<Integer> participantIds = cn.hutool.json.JSONUtil.toList(
                        cn.hutool.json.JSONUtil.parseArray(requirement.getParticipants()), Integer.class);
                for (Integer pid : participantIds) {
                    if (pid != null && !notified.contains(pid)) {
                        notified.add(pid);
                        contexts.add(MessageContext.builder()
                                .eventType(MessageEventType.REQ_UPDATED)
                                .senderId(loginId)
                                .receiverId(pid)
                                .receiverRole("PARTICIPANT")
                                .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                                .bizType("requirement")
                                .bizId(requirement.getId())
                                .params(params)
                                .snapshot(requirementSnapshot)
                                .build());
                    }
                }
            } catch (Exception e) {
                log.warn("需求参与人解析失败，跳过参与人通知，requirementId={}，participants={}", requirement.getId(), requirement.getParticipants(), e);
            }
        }
        if (requirement.getCreateUserId() != null && !notified.contains(requirement.getCreateUserId())) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.REQ_UPDATED)
                    .senderId(loginId)
                    .receiverId(requirement.getCreateUserId())
                    .receiverRole("CREATOR")
                    .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .params(params)
                    .snapshot(requirementSnapshot)
                    .build());
        }
        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    /**
     * 发送需求状态变更通知
     */
    private void sendRequirementStatusChangedMessage(Requirement requirement, String oldStatus, String newStatus, Integer loginId) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("reqCode", requirement.getReqCode());
        params.put("reqTitle", requirement.getTitle());
        params.put("operatorName", getOperatorName(loginId));
        params.put("priority", requirement.getPriority());
        params.put("oldStatus", oldStatus);
        params.put("newStatus", newStatus);
        params.put("projectName", getProjectName(requirement.getProjectId()));
        params.put("moduleName", getModuleName(requirement.getModuleId()));
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        java.util.Set<Integer> notified = new java.util.HashSet<>();
        Map<String, Object> requirementSnapshot = buildRequirementSnapshot(requirement);

        // 通知负责人
        if (requirement.getOwnerId() != null) {
            notified.add(requirement.getOwnerId());
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.REQ_STATUS_CHANGED)
                    .senderId(loginId)
                    .receiverId(requirement.getOwnerId())
                    .receiverRole("OWNER")
                    .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .params(params)
                    .snapshot(requirementSnapshot)
                    .build());
        }

        // 通知参与人
        if (requirement.getParticipants() != null && !requirement.getParticipants().isEmpty()) {
            try {
                java.util.List<Integer> participantIds = cn.hutool.json.JSONUtil.toList(
                        cn.hutool.json.JSONUtil.parseArray(requirement.getParticipants()), Integer.class);
                for (Integer pid : participantIds) {
                    if (pid != null && !notified.contains(pid)) {
                        notified.add(pid);
                        contexts.add(MessageContext.builder()
                                .eventType(MessageEventType.REQ_STATUS_CHANGED)
                                .senderId(loginId)
                                .receiverId(pid)
                                .receiverRole("PARTICIPANT")
                                .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                                .bizType("requirement")
                                .bizId(requirement.getId())
                                .params(params)
                                .snapshot(requirementSnapshot)
                                .build());
                    }
                }
            } catch (Exception e) {
                log.warn("需求参与人解析失败，跳过参与人通知，requirementId={}，participants={}", requirement.getId(), requirement.getParticipants(), e);
            }
        }

        // 通知创建人
        if (requirement.getCreateUserId() != null && !notified.contains(requirement.getCreateUserId())) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.REQ_STATUS_CHANGED)
                    .senderId(loginId)
                    .receiverId(requirement.getCreateUserId())
                    .receiverRole("CREATOR")
                    .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .params(params)
                    .snapshot(requirementSnapshot)
                    .build());
        }

        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    /**
     * 发送需求删除通知
     */
    private void sendRequirementDeletedMessage(Requirement requirement, Integer loginId) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("reqCode", requirement.getReqCode());
        params.put("reqTitle", requirement.getTitle());
        params.put("operatorName", getOperatorName(loginId));
        params.put("projectName", getProjectName(requirement.getProjectId()));
        params.put("moduleName", getModuleName(requirement.getModuleId()));
        params.put("operateTime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        java.util.Set<Integer> notified = new java.util.HashSet<>();
        Map<String, Object> requirementSnapshot = buildRequirementSnapshot(requirement);

        // 通知负责人
        if (requirement.getOwnerId() != null) {
            notified.add(requirement.getOwnerId());
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.REQ_DELETED)
                    .senderId(loginId)
                    .receiverId(requirement.getOwnerId())
                    .receiverRole("OWNER")
                    .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .params(params)
                    .snapshot(requirementSnapshot)
                    .build());
        }

        // 通知参与人
        if (requirement.getParticipants() != null && !requirement.getParticipants().isEmpty()) {
            try {
                java.util.List<Integer> participantIds = cn.hutool.json.JSONUtil.toList(
                        cn.hutool.json.JSONUtil.parseArray(requirement.getParticipants()), Integer.class);
                for (Integer pid : participantIds) {
                    if (pid != null && !notified.contains(pid)) {
                        notified.add(pid);
                        contexts.add(MessageContext.builder()
                                .eventType(MessageEventType.REQ_DELETED)
                                .senderId(loginId)
                                .receiverId(pid)
                                .receiverRole("PARTICIPANT")
                                .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                                .bizType("requirement")
                                .bizId(requirement.getId())
                                .params(params)
                                .snapshot(requirementSnapshot)
                                .build());
                    }
                }
            } catch (Exception e) {
                log.warn("需求参与人解析失败，跳过参与人通知，requirementId={}，participants={}", requirement.getId(), requirement.getParticipants(), e);
            }
        }

        // 「创建人」角色被项目启用时，删除需求也通知创建人（默认关闭）
        if (requirement.getCreateUserId() != null && !notified.contains(requirement.getCreateUserId())
                && notifyDispatchService.isRoleEnabled(requirement.getProjectId(), MessageEventType.REQ_DELETED, "CREATOR")) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.REQ_DELETED)
                    .senderId(loginId)
                    .receiverId(requirement.getCreateUserId())
                    .receiverRole("CREATOR")
                    .projectId(requirement.getProjectId()).teamId(getTeamIdByProjectId(requirement.getProjectId()))
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .params(params)
                    .snapshot(requirementSnapshot)
                    .build());
        }

        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    /**
     * 构建需求快照（用于消息详情展示）
     */
    private Map<String, Object> buildRequirementSnapshot(Requirement requirement) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("reqCode", requirement.getReqCode());
        snapshot.put("title", requirement.getTitle());
        snapshot.put("description", requirement.getDescription());
        snapshot.put("status", requirement.getStatus());
        snapshot.put("priority", requirement.getPriority());
        snapshot.put("reqType", requirement.getReqType());
        snapshot.put("source", requirement.getSource());
        snapshot.put("ownerId", requirement.getOwnerId());
        snapshot.put("participants", requirement.getParticipants());
        snapshot.put("expectReleaseTime", requirement.getExpectReleaseTime());
        snapshot.put("tags", requirement.getTags());
        snapshot.put("version", requirement.getVersion());
        snapshot.put("moduleId", requirement.getModuleId());
        snapshot.put("parentId", requirement.getParentId());
        fillProjectTeamInfo(snapshot, requirement.getProjectId());
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

    @Override
    public SaResult getTraceability(Integer requirementId) {
        if (requirementId == null) {
            return SaResult.error("缺少需求ID");
        }
        Requirement requirement = getById(requirementId);
        if (requirement == null) {
            return SaResult.error("需求不存在");
        }

        // 查询关联用例
        QueryWrapper<TestCase> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("requirement_id", requirementId);
        List<TestCase> testCases = testCaseMapper.selectList(caseWrapper);

        // 查询关联BUG（按用例分组 + 直接关联需求）
        List<Integer> caseIds = testCases.stream().map(TestCase::getId).collect(Collectors.toList());
        Map<Integer, List<Map<String, Object>>> bugsByCase = new HashMap<>();
        List<Map<String, Object>> directBugs = new ArrayList<>();
        int totalBugs = 0, openBugs = 0;

        // 1. 通过用例关联的BUG
        if (!caseIds.isEmpty()) {
            QueryWrapper<com.mokatest.platform.demos.qa.domain.Bug> bugWrapper = new QueryWrapper<>();
            bugWrapper.in("test_case_id", caseIds);
            List<com.mokatest.platform.demos.qa.domain.Bug> bugs = bugMapper.selectList(bugWrapper);
            for (com.mokatest.platform.demos.qa.domain.Bug bug : bugs) {
                Map<String, Object> bugMap = buildBugMap(bug);
                bugsByCase.computeIfAbsent(bug.getTestCaseId(), k -> new ArrayList<>()).add(bugMap);
                totalBugs++;
                if (!"CLOSED".equals(bug.getStatus()) && !"REJECTED".equals(bug.getStatus())) {
                    openBugs++;
                }
            }
        }

        // 2. 直接关联需求但未关联用例的BUG
        QueryWrapper<com.mokatest.platform.demos.qa.domain.Bug> directBugWrapper = new QueryWrapper<>();
        directBugWrapper.eq("requirement_id", requirementId)
                .and(w -> w.isNull("test_case_id").or().eq("test_case_id", 0));
        List<com.mokatest.platform.demos.qa.domain.Bug> directBugList = bugMapper.selectList(directBugWrapper);
        for (com.mokatest.platform.demos.qa.domain.Bug bug : directBugList) {
            directBugs.add(buildBugMap(bug));
            totalBugs++;
            if (!"CLOSED".equals(bug.getStatus()) && !"REJECTED".equals(bug.getStatus())) {
                openBugs++;
            }
        }

        // 组装用例数据
        List<Map<String, Object>> caseList = new ArrayList<>();
        for (TestCase tc : testCases) {
            Map<String, Object> caseMap = new HashMap<>();
            caseMap.put("id", tc.getId());
            caseMap.put("caseCode", tc.getCaseCode());
            caseMap.put("caseName", tc.getCaseName());
            caseMap.put("caseType", tc.getCaseType());
            caseMap.put("priority", tc.getPriority());
            caseMap.put("status", tc.getStatus());
            caseMap.put("lastResult", tc.getLastResult());
            caseMap.put("bugs", bugsByCase.getOrDefault(tc.getId(), new ArrayList<>()));
            caseList.add(caseMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("requirement", requirement);
        result.put("testCases", caseList);
        result.put("directBugs", directBugs);
        result.put("caseCount", testCases.size());
        result.put("bugCount", totalBugs);
        result.put("openBugCount", openBugs);
        return SaResult.ok().setData(result);
    }

    private Map<String, Object> buildBugMap(com.mokatest.platform.demos.qa.domain.Bug bug) {
        Map<String, Object> bugMap = new HashMap<>();
        bugMap.put("id", bug.getId());
        bugMap.put("bugCode", bug.getBugCode());
        bugMap.put("title", bug.getTitle());
        bugMap.put("status", bug.getStatus());
        bugMap.put("severity", bug.getSeverity());
        return bugMap;
    }

    private static final java.util.concurrent.ThreadLocalRandom REQ_RANDOM = java.util.concurrent.ThreadLocalRandom.current();
    private static final java.util.concurrent.atomic.AtomicInteger REQ_SEQ = new java.util.concurrent.atomic.AtomicInteger(0);

    private String generateReqCode() {
        String dateStr = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
        int seq = REQ_SEQ.incrementAndGet() % 10000;
        int random = REQ_RANDOM.nextInt(1000, 9999);
        return "REQ-" + dateStr + "-" + String.format("%04d%04d", seq, random);
    }

    @Override
    public SaResult stats(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("total", baseMapper.selectCount(new QueryWrapper<Requirement>().eq("project_id", projectId)));
        map.put("developing", baseMapper.selectCount(new QueryWrapper<Requirement>().eq("project_id", projectId).eq("status", "DEVELOPING")));
        map.put("testing", baseMapper.selectCount(new QueryWrapper<Requirement>().eq("project_id", projectId).eq("status", "TESTING")));
        map.put("closed", baseMapper.selectCount(new QueryWrapper<Requirement>().eq("project_id", projectId).eq("status", "CLOSED")));
        return SaResult.data(map);
    }
}
