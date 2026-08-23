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
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.vo.TestCaseVO;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import com.mokatest.platform.demos.qa.message.dto.MessageContext;
import com.mokatest.platform.demos.qa.message.enums.MessageEventType;
import com.mokatest.platform.demos.qa.message.service.MessageService;
import com.mokatest.platform.demos.qa.message.service.impl.NotifyDispatchService;
import com.mokatest.platform.demos.qa.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * 文字用例 Service 实现
 */
@Service
@RequiredArgsConstructor
public class TestCaseServiceImpl extends ServiceImpl<TestCaseMapper, TestCase> implements TestCaseService {

    private final UserMapper userMapper;
    private final com.mokatest.platform.demos.qa.mapper.QaModuleMapper qaModuleMapper;
    private final com.mokatest.platform.demos.qa.mapper.TestPlanCaseMapper testPlanCaseMapper;
    private final com.mokatest.platform.demos.qa.mapper.RequirementMapper requirementMapper;
    private final com.mokatest.platform.demos.qa.mapper.BugMapper bugMapper;
    private final com.mokatest.platform.demos.qa.mapper.BugCaseMapper bugCaseMapper;
    private final com.mokatest.platform.demos.qa.mapper.TestCaseAutoBindMapper testCaseAutoBindMapper;
    private final com.mokatest.platform.demos.qa.service.TestCaseSetService testCaseSetService;
    private final com.mokatest.platform.demos.qa.mapper.TestCaseSetRelationMapper testCaseSetRelationMapper;
    private final com.mokatest.platform.demos.qa.service.TestCaseOperationLogService testCaseOperationLogService;
    private final com.mokatest.platform.demos.qa.service.TestCaseVersionService testCaseVersionService;
    private final com.mokatest.platform.demos.qa.service.TestCaseReviewService testCaseReviewService;
    private final ProjectMapper projectMapper;
    private final MessageService messageService;
    private final NotifyDispatchService notifyDispatchService;

    @Override
    public SaResult listByProject(Integer projectId, Integer moduleId, Integer setId, Integer requirementId, String keyword, String lastResult, Integer page, Integer pageSize, Integer excludePlanId, String status) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        QueryWrapper<TestCase> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        if (moduleId != null) {
            Set<Integer> moduleIds = collectDescendantModuleIds(projectId, moduleId);
            if (moduleIds.isEmpty()) {
                moduleIds = Collections.singleton(moduleId);
            }
            wrapper.in("module_id", moduleIds);
        }
        if (setId != null) {
            List<Integer> caseIds = testCaseSetService.getCaseIdsBySetId(setId);
            if (caseIds.isEmpty()) {
                // 无关联用例，返回空结果
                Map<String, Object> emptyData = new HashMap<>();
                emptyData.put("records", Collections.emptyList());
                emptyData.put("total", 0L);
                emptyData.put("current", page != null ? page : 1);
                emptyData.put("size", pageSize != null ? pageSize : 10);
                return SaResult.ok().setData(emptyData);
            }
            wrapper.in("id", caseIds);
        }
        if (requirementId != null) {
            // 多需求关联：旧字段（首需求）精确匹配 + 新字段 FIND_IN_SET 匹配
            wrapper.and(w -> w.eq("requirement_id", requirementId)
                    .or().apply("FIND_IN_SET({0}, requirement_ids)", requirementId));
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("case_name", keyword).or().like("case_code", keyword));
        }
        if (lastResult != null && !lastResult.isEmpty()) {
            wrapper.eq("last_result", lastResult);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        // 排除已添加到指定计划的用例
        if (excludePlanId != null) {
            QueryWrapper<com.mokatest.platform.demos.qa.domain.TestPlanCase> planCaseWrapper = new QueryWrapper<>();
            planCaseWrapper.eq("plan_id", excludePlanId).select("test_case_id");
            List<com.mokatest.platform.demos.qa.domain.TestPlanCase> planCases = testPlanCaseMapper.selectList(planCaseWrapper);
            if (!planCases.isEmpty()) {
                List<Integer> excludeCaseIds = planCases.stream()
                        .map(com.mokatest.platform.demos.qa.domain.TestPlanCase::getTestCaseId)
                        .collect(Collectors.toList());
                wrapper.notIn("id", excludeCaseIds);
            }
        }
        // 同模块内按 sort 升序（脑图顺序），未排序的历史数据（sort=0）回退为创建时间倒序
        wrapper.orderByAsc("sort").orderByDesc("create_time");

        IPage<TestCase> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        IPage<TestCase> result = baseMapper.selectPage(pageParam, wrapper);

        Map<String, Object> data = new HashMap<>();
        // 补充创建人名称和模块名称
        List<TestCase> records = result.getRecords();
        if (!records.isEmpty()) {
            Set<Integer> userIds = records.stream()
                    .flatMap(tc -> java.util.stream.Stream.of(tc.getCreateUserId(), tc.getUpdateUserId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Integer, String> userMap = new HashMap<>();
            if (!userIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(userIds);
                userMap = users.stream()
                        .collect(Collectors.toMap(u -> u.getId().intValue(), User::getUsername, (a, b) -> a));
            }

            Set<Integer> moduleIds = records.stream()
                    .map(TestCase::getModuleId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Integer, String> moduleMap = new HashMap<>();
            if (!moduleIds.isEmpty()) {
                List<com.mokatest.platform.demos.qa.domain.QaModule> modules = qaModuleMapper.selectBatchIds(moduleIds);
                moduleMap = modules.stream()
                        .collect(Collectors.toMap(com.mokatest.platform.demos.qa.domain.QaModule::getId, com.mokatest.platform.demos.qa.domain.QaModule::getModuleName, (a, b) -> a));
            }

            // 查询需求标题（多需求关联：requirement_ids 逗号分隔 + 旧字段 requirement_id 一起收集）
            Set<Integer> reqIds = new HashSet<>();
            for (TestCase tc : records) {
                if (tc.getRequirementId() != null) {
                    reqIds.add(tc.getRequirementId());
                }
                reqIds.addAll(parseReqIds(tc.getRequirementIds()));
            }
            final Map<Integer, String> reqMap;
            if (!reqIds.isEmpty()) {
                List<com.mokatest.platform.demos.qa.domain.Requirement> reqs = requirementMapper.selectBatchIds(reqIds);
                reqMap = reqs.stream()
                        .collect(Collectors.toMap(com.mokatest.platform.demos.qa.domain.Requirement::getId, com.mokatest.platform.demos.qa.domain.Requirement::getTitle, (a, b) -> a));
            } else {
                reqMap = Collections.emptyMap();
            }

            // 查询关联BUG数量（通过 bug_case 关联表）
            List<Integer> caseIds = records.stream().map(TestCase::getId).collect(Collectors.toList());
            Map<Integer, Long> bugCountMap = new HashMap<>();
            if (!caseIds.isEmpty()) {
                QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> bugWrapper = new QueryWrapper<>();
                bugWrapper.in("test_case_id", caseIds)
                        .select("test_case_id", "COUNT(*) AS cnt")
                        .groupBy("test_case_id");
                List<Map<String, Object>> bugCountList = bugCaseMapper.selectMaps(bugWrapper);
                bugCountMap = bugCountList.stream()
                        .collect(Collectors.toMap(
                                m -> ((Number) m.get("test_case_id")).intValue(),
                                m -> ((Number) m.get("cnt")).longValue(),
                                (a, b) -> a));
            }

            // 查询所属测试集
            Map<Integer, List<Integer>> caseSetIdsMap = new HashMap<>();
            Map<Integer, String> caseSetNamesMap = new HashMap<>();
            if (!caseIds.isEmpty()) {
                QueryWrapper<com.mokatest.platform.demos.qa.domain.TestCaseSetRelation> relationWrapper = new QueryWrapper<>();
                relationWrapper.in("test_case_id", caseIds);
                List<com.mokatest.platform.demos.qa.domain.TestCaseSetRelation> relations = testCaseSetRelationMapper.selectList(relationWrapper);
                Set<Integer> setIds = relations.stream()
                        .map(com.mokatest.platform.demos.qa.domain.TestCaseSetRelation::getSetId)
                        .collect(Collectors.toSet());
                Map<Integer, String> setNameMap = new HashMap<>();
                if (!setIds.isEmpty()) {
                    List<com.mokatest.platform.demos.qa.domain.TestCaseSet> sets = testCaseSetService.listByIds(setIds);
                    setNameMap = sets.stream()
                            .collect(Collectors.toMap(com.mokatest.platform.demos.qa.domain.TestCaseSet::getId, com.mokatest.platform.demos.qa.domain.TestCaseSet::getSetName, (a, b) -> a));
                }
                for (com.mokatest.platform.demos.qa.domain.TestCaseSetRelation relation : relations) {
                    caseSetIdsMap.computeIfAbsent(relation.getTestCaseId(), k -> new ArrayList<>()).add(relation.getSetId());
                }
                for (Map.Entry<Integer, List<Integer>> entry : caseSetIdsMap.entrySet()) {
                    String names = entry.getValue().stream()
                            .map(setNameMap::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(","));
                    caseSetNamesMap.put(entry.getKey(), names);
                }
            }

            // 将数据转为VO后添加扩展字段
            List<TestCaseVO> voList = new ArrayList<>();
            for (TestCase tc : records) {
                TestCaseVO vo = new TestCaseVO();
                vo.setId(tc.getId());
                vo.setCaseCode(tc.getCaseCode());
                vo.setCaseName(tc.getCaseName());
                vo.setPreCondition(tc.getPreCondition());
                vo.setTestSteps(tc.getTestSteps());
                vo.setCaseType(tc.getCaseType());
                vo.setPriority(tc.getPriority());
                vo.setStatus(tc.getStatus());
                vo.setProjectId(tc.getProjectId());
                vo.setRequirementId(tc.getRequirementId());
                vo.setRequirementTitle(reqMap.getOrDefault(tc.getRequirementId(), ""));
                // 多需求关联：未写新字段的旧数据回退展示旧字段
                String reqIdsStr = (tc.getRequirementIds() != null && !tc.getRequirementIds().isEmpty())
                        ? tc.getRequirementIds()
                        : (tc.getRequirementId() != null ? String.valueOf(tc.getRequirementId()) : "");
                vo.setRequirementIds(reqIdsStr);
                vo.setRequirementTitles(parseReqIds(reqIdsStr).stream()
                        .map(rid -> reqMap.getOrDefault(rid, ""))
                        .filter(t -> !t.isEmpty())
                        .collect(Collectors.joining("、")));
                vo.setBugCount(bugCountMap.getOrDefault(tc.getId(), 0L));
                vo.setCreateUserId(tc.getCreateUserId());
                vo.setCreateUserName(userMap.getOrDefault(tc.getCreateUserId(), ""));
                vo.setUpdateUserId(tc.getUpdateUserId());
                vo.setUpdateUserName(userMap.getOrDefault(tc.getUpdateUserId(), ""));
                vo.setModuleId(tc.getModuleId());
                vo.setModuleName(moduleMap.getOrDefault(tc.getModuleId(), ""));
                vo.setSetIds(caseSetIdsMap.getOrDefault(tc.getId(), Collections.emptyList()));
                vo.setSetNames(caseSetNamesMap.getOrDefault(tc.getId(), ""));
                vo.setLastResult(tc.getLastResult());
                vo.setLastExecuteTime(tc.getLastExecuteTime());
                vo.setTags(tc.getTags());
                vo.setExpectDuration(tc.getExpectDuration());
                vo.setCreateTime(tc.getCreateTime());
                vo.setUpdateTime(tc.getUpdateTime());
                voList.add(vo);
            }
            data.put("records", voList);
        } else {
            data.put("records", records);
        }
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        return SaResult.ok().setData(data);
    }

    @Override
    public SaResult listIdsByProject(Integer projectId, Integer moduleId, Integer setId, Integer requirementId, String keyword, String lastResult, Integer excludePlanId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        QueryWrapper<TestCase> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId).select("id");
        if (moduleId != null) {
            Set<Integer> moduleIds = collectDescendantModuleIds(projectId, moduleId);
            if (moduleIds.isEmpty()) {
                moduleIds = Collections.singleton(moduleId);
            }
            wrapper.in("module_id", moduleIds);
        }
        if (setId != null) {
            List<Integer> caseIds = testCaseSetService.getCaseIdsBySetId(setId);
            if (caseIds.isEmpty()) {
                return SaResult.ok().setData(Collections.emptyList());
            }
            wrapper.in("id", caseIds);
        }
        if (requirementId != null) {
            // 多需求关联：旧字段（首需求）精确匹配 + 新字段 FIND_IN_SET 匹配
            wrapper.and(w -> w.eq("requirement_id", requirementId)
                    .or().apply("FIND_IN_SET({0}, requirement_ids)", requirementId));
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("case_name", keyword).or().like("case_code", keyword));
        }
        if (lastResult != null && !lastResult.isEmpty()) {
            wrapper.eq("last_result", lastResult);
        }
        if (excludePlanId != null) {
            QueryWrapper<com.mokatest.platform.demos.qa.domain.TestPlanCase> planCaseWrapper = new QueryWrapper<>();
            planCaseWrapper.eq("plan_id", excludePlanId).select("test_case_id");
            List<com.mokatest.platform.demos.qa.domain.TestPlanCase> planCases = testPlanCaseMapper.selectList(planCaseWrapper);
            if (!planCases.isEmpty()) {
                List<Integer> excludeCaseIds = planCases.stream()
                        .map(com.mokatest.platform.demos.qa.domain.TestPlanCase::getTestCaseId)
                        .collect(Collectors.toList());
                wrapper.notIn("id", excludeCaseIds);
            }
        }
        List<TestCase> list = baseMapper.selectList(wrapper);
        List<Integer> ids = list.stream().map(TestCase::getId).collect(Collectors.toList());
        return SaResult.ok().setData(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult saveOrUpdateCase(TestCase testCase) {
        if (testCase == null) {
            return SaResult.error("缺少参数");
        }
        if (testCase.getProjectId() == null) {
            return SaResult.error("缺少项目ID");
        }
        boolean isNew = testCase.getId() == null;
        // 新建必须有用例名称；编辑（含行内局部更新）只有显式传了名称但为空白时才拦截，
        // 未传名称表示本次不改名称，不能按新建校验（MyBatis-Plus updateById 忽略 null 字段，不会覆盖原值）
        if (testCase.getCaseName() == null || testCase.getCaseName().trim().isEmpty()) {
            if (isNew || testCase.getCaseName() != null) {
                return SaResult.error("用例名称不能为空");
            }
        }
        if (isNew && (testCase.getCaseCode() == null || testCase.getCaseCode().trim().isEmpty())) {
            // 先占一个临时唯一值，等插入拿到自增 id 后再改成 CASE-{id}
            testCase.setCaseCode("TMP-" + System.nanoTime());
        }
        // 默认值仅新建时补；编辑时保持 null，由 updateById 忽略，避免行内局部更新把未传字段冲掉
        if (isNew) {
            if (testCase.getCaseType() == null) {
                testCase.setCaseType("FUNCTION");
            }
            if (testCase.getPriority() == null) {
                testCase.setPriority("P1");
            }
            // 用例状态只能由评审流程/状态流转接口驱动，新建一律 DRAFT，不接受表单传入
            testCase.setStatus("DRAFT");
        } else {
            // 编辑保存不允许携带状态变更（防止绕过评审流程直接置 REVIEWED 等）
            testCase.setStatus(null);
        }

        // 多需求关联字段同步：传了 requirement_ids 则以它为准（requirement_id 同步为首个，兼容旧逻辑）；
        // 只传了旧字段 requirement_id（如 AI 生成、BUG 回归沉淀）则回填 requirement_ids
        boolean requirementIdsProvided = testCase.getRequirementIds() != null;
        if (requirementIdsProvided) {
            List<Integer> ids = parseReqIds(testCase.getRequirementIds());
            testCase.setRequirementIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
            testCase.setRequirementId(ids.isEmpty() ? null : ids.get(0));
        } else if (testCase.getRequirementId() != null) {
            testCase.setRequirementIds(String.valueOf(testCase.getRequirementId()));
        }

        List<Integer> setIds = testCase.getSetIds();
        // 保存前先清空临时字段，避免 MyBatis-Plus 误处理
        testCase.setSetIds(null);

        Integer loginId = StpUtil.getLoginIdAsInt();
        if (testCase.getId() == null) {
            testCase.setCreateUserId(loginId);
            testCase.setCreateTime(new Date());
        }
        testCase.setUpdateUserId(loginId);
        testCase.setUpdateTime(new Date());

        // 编辑时先取旧用例，用于操作日志比对和 REVIEWED 基线版本快照
        TestCase oldCase = null;
        if (!isNew) {
            oldCase = getById(testCase.getId());
            if (oldCase == null) {
                return SaResult.error("用例不存在");
            }
        }

        boolean success = saveOrUpdate(testCase);
        if (!success) {
            return SaResult.error("保存失败");
        }
        // 新增用例时，把临时编号改为短格式 CASE-{id}
        if (isNew && testCase.getCaseCode() != null && testCase.getCaseCode().startsWith("TMP-")) {
            testCase.setCaseCode("CASE-" + testCase.getId());
            updateById(testCase);
        }

        // 显式同步旧字段 requirement_id：updateById 会忽略 null，清空全部关联时需强制写 NULL
        if (!isNew && requirementIdsProvided) {
            UpdateWrapper<TestCase> syncWrapper = new UpdateWrapper<>();
            syncWrapper.eq("id", testCase.getId()).set("requirement_id", testCase.getRequirementId());
            baseMapper.update(null, syncWrapper);
        }

        // 所属模块根节点约定：前端表单选「全部用例」传 moduleId=0，统一归一化为 NULL 存储
        // （行内局部更新不传 moduleId，不受影响；新建时 0 也归一化为 NULL）
        if (testCase.getModuleId() != null && testCase.getModuleId() == 0) {
            testCase.setModuleId(null);
            if (!isNew) {
                UpdateWrapper<TestCase> moduleSync = new UpdateWrapper<>();
                moduleSync.eq("id", testCase.getId()).set("module_id", null);
                baseMapper.update(null, moduleSync);
            }
        }

        // 同步测试集关联
        if (setIds != null) {
            testCaseSetService.bindSets(testCase.getId(), setIds);
        }

        // 操作日志埋点
        if (isNew) {
            testCaseOperationLogService.log(testCase.getId(), "CREATE", null, null, null, null);
        } else if (oldCase != null) {
            logCaseUpdateChanges(oldCase, testCase);
            // REVIEWED 基线后内容有实质变化 → 先存旧版本快照
            if ("REVIEWED".equals(oldCase.getStatus()) && hasSubstantiveChange(oldCase, testCase)) {
                testCaseVersionService.snapshot(oldCase, "已评审基线被修改，自动留存旧版本快照");
                testCaseOperationLogService.log(testCase.getId(), "VERSION", null, null, null,
                        "已评审用例内容变更，旧版本已留存快照");
            }
        }

        // 发送用例创建/更新通知给创建人自己（buildMessage 会过滤"不发给自己"，所以如果创建人就是登录人则跳过）
        if (isNew) {
            sendCaseCreatedMessage(testCase, loginId);
        } else {
            sendCaseUpdatedMessage(testCase, loginId);
        }

        return SaResult.ok().setData(testCase.getId());
    }

    /**
     * 解析逗号分隔的需求ID字符串
     */
    private List<Integer> parseReqIds(String requirementIds) {
        if (requirementIds == null || requirementIds.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> ids = new ArrayList<>();
        for (String s : requirementIds.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                try {
                    ids.add(Integer.valueOf(t));
                } catch (NumberFormatException ignored) {
                    // 忽略脏数据
                }
            }
        }
        return ids;
    }

    /**
     * 编辑用例时比对主要字段，生成 UPDATE 操作日志（每个变更字段一条）
     */
    private void logCaseUpdateChanges(TestCase oldCase, TestCase newCase) {
        logFieldIfChanged(oldCase.getId(), "用例名称", oldCase.getCaseName(), newCase.getCaseName());
        logFieldIfChanged(oldCase.getId(), "优先级", oldCase.getPriority(), newCase.getPriority());
        logFieldIfChanged(oldCase.getId(), "状态", oldCase.getStatus(), newCase.getStatus());
        logFieldIfChanged(oldCase.getId(), "前置条件", oldCase.getPreCondition(), newCase.getPreCondition());
        logFieldIfChanged(oldCase.getId(), "所属模块",
                oldCase.getModuleId() != null ? String.valueOf(oldCase.getModuleId()) : null,
                newCase.getModuleId() != null ? String.valueOf(newCase.getModuleId()) : null);
        logFieldIfChanged(oldCase.getId(), "关联需求", oldCase.getRequirementIds(), newCase.getRequirementIds());
        logFieldIfChanged(oldCase.getId(), "标签", oldCase.getTags(), newCase.getTags());
    }

    private void logFieldIfChanged(Integer caseId, String fieldName, String oldValue, String newValue) {
        // 新值为 null 视为未提交该字段（部分更新），不记日志
        if (newValue == null || Objects.equals(oldValue, newValue)) {
            return;
        }
        testCaseOperationLogService.log(caseId, "UPDATE", fieldName, oldValue, newValue, null);
    }

    /**
     * 判断编辑是否对用例内容有实质变化（用于 REVIEWED 基线快照）
     */
    private boolean hasSubstantiveChange(TestCase oldCase, TestCase newCase) {
        return (newCase.getCaseName() != null && !Objects.equals(oldCase.getCaseName(), newCase.getCaseName()))
                || (newCase.getPreCondition() != null && !Objects.equals(oldCase.getPreCondition(), newCase.getPreCondition()))
                || (newCase.getTestSteps() != null && !Objects.equals(
                        oldCase.getTestSteps() != null ? com.alibaba.fastjson.JSON.toJSONString(oldCase.getTestSteps()) : null,
                        com.alibaba.fastjson.JSON.toJSONString(newCase.getTestSteps())))
                || (newCase.getPriority() != null && !Objects.equals(oldCase.getPriority(), newCase.getPriority()))
                || (newCase.getCaseType() != null && !Objects.equals(oldCase.getCaseType(), newCase.getCaseType()))
                || (newCase.getTags() != null && !Objects.equals(oldCase.getTags(), newCase.getTags()))
                || (newCase.getModuleId() != null && !Objects.equals(oldCase.getModuleId(), newCase.getModuleId()))
                || (newCase.getRequirementIds() != null && !Objects.equals(
                        oldCase.getRequirementIds() != null ? oldCase.getRequirementIds() : "",
                        newCase.getRequirementIds()))
                || (newCase.getExpectDuration() != null && !Objects.equals(oldCase.getExpectDuration(), newCase.getExpectDuration()));
    }

    @Override
    @Transactional
    public SaResult deleteCase(Integer id) {
        if (id == null) {
            return SaResult.error("缺少ID");
        }
        TestCase testCase = getById(id);
        if (testCase == null) {
            return SaResult.error("用例不存在");
        }
        // 0. 删除前写操作日志
        testCaseOperationLogService.log(id, "DELETE", null, null, null, null);

        // 删除前发送通知（先发通知再删数据，参考 BugServiceImpl.deleteBug）
        Integer loginId = StpUtil.getLoginIdAsInt();
        sendCaseDeletedMessage(testCase, loginId);

        // 1. 解绑关联 BUG（从 bug_case 关联表删除）
        QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> bugCaseDelWrapper = new QueryWrapper<>();
        bugCaseDelWrapper.eq("test_case_id", id);
        bugCaseMapper.delete(bugCaseDelWrapper);

        // 2. 清理自动化绑定
        QueryWrapper<com.mokatest.platform.demos.qa.domain.TestCaseAutoBind> bindWrapper = new QueryWrapper<>();
        bindWrapper.eq("test_case_id", id);
        testCaseAutoBindMapper.delete(bindWrapper);

        // 3. 清理测试计划用例关联
        QueryWrapper<com.mokatest.platform.demos.qa.domain.TestPlanCase> planCaseWrapper = new QueryWrapper<>();
        planCaseWrapper.eq("test_case_id", id);
        testPlanCaseMapper.delete(planCaseWrapper);

        // 4. 清理测试集关联
        QueryWrapper<com.mokatest.platform.demos.qa.domain.TestCaseSetRelation> setRelationWrapper = new QueryWrapper<>();
        setRelationWrapper.eq("test_case_id", id);
        testCaseSetRelationMapper.delete(setRelationWrapper);

        // 5. 从进行中的评审单移除（删关联+明细、重算 finalResult，必要时完结评审单）
        testCaseReviewService.removeFromActiveReviews(java.util.Collections.singletonList(id));

        // 6. 逻辑删除用例本身
        testCase.setDeletedAt(new Date());
        boolean success = baseMapper.deleteById(testCase) > 0;
        return success ? SaResult.ok("删除成功") : SaResult.error("删除失败");
    }

    @Override
    @Transactional
    public SaResult batchDeleteCase(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return SaResult.error("未选择要删除的用例");
        }
        // 0. 删除前逐条写操作日志
        for (Integer id : ids) {
            testCaseOperationLogService.log(id, "DELETE", null, null, null, null);
        }

        // 1. 解绑关联 BUG（从 bug_case 关联表删除）
        QueryWrapper<com.mokatest.platform.demos.qa.domain.BugCase> bugCaseDelWrapper = new QueryWrapper<>();
        bugCaseDelWrapper.in("test_case_id", ids);
        bugCaseMapper.delete(bugCaseDelWrapper);

        // 2. 清理自动化绑定
        QueryWrapper<com.mokatest.platform.demos.qa.domain.TestCaseAutoBind> bindWrapper = new QueryWrapper<>();
        bindWrapper.in("test_case_id", ids);
        testCaseAutoBindMapper.delete(bindWrapper);

        // 3. 清理测试计划用例关联
        QueryWrapper<com.mokatest.platform.demos.qa.domain.TestPlanCase> planCaseWrapper = new QueryWrapper<>();
        planCaseWrapper.in("test_case_id", ids);
        testPlanCaseMapper.delete(planCaseWrapper);

        // 4. 清理测试集关联
        QueryWrapper<com.mokatest.platform.demos.qa.domain.TestCaseSetRelation> setRelationWrapper = new QueryWrapper<>();
        setRelationWrapper.in("test_case_id", ids);
        testCaseSetRelationMapper.delete(setRelationWrapper);

        // 5. 从进行中的评审单移除（删关联+明细、重算 finalResult，必要时完结评审单）
        testCaseReviewService.removeFromActiveReviews(ids);

        // 6. 批量逻辑删除用例（逐条设置 deleted_at）
        List<TestCase> cases = baseMapper.selectBatchIds(ids);
        Date now = new Date();
        Integer loginId = StpUtil.getLoginIdAsInt();
        for (TestCase testCase : cases) {
            if (testCase != null) {
                // 删除前发送通知（先发通知再删数据，参考 BugServiceImpl.deleteBug）
                sendCaseDeletedMessage(testCase, loginId);
                testCase.setDeletedAt(now);
                baseMapper.deleteById(testCase);
            }
        }
        return SaResult.ok("批量删除成功");
    }

    @Override
    @Transactional
    public SaResult batchMoveModule(List<Integer> ids, Integer moduleId) {
        if (ids == null || ids.isEmpty()) {
            return SaResult.error("未选择要移动的用例");
        }
        if (moduleId == null) {
            return SaResult.error("请选择目标模块");
        }
        // 0 归一化：moduleId=0 表示根节点，存储为 NULL
        Integer targetModuleId = moduleId == 0 ? null : moduleId;
        for (Integer id : ids) {
            testCaseOperationLogService.log(id, "UPDATE", "moduleId", null, null, null);
        }
        UpdateWrapper<TestCase> updateWrapper = new UpdateWrapper<>();
        updateWrapper.in("id", ids).set("module_id", targetModuleId);
        baseMapper.update(null, updateWrapper);
        return SaResult.ok("批量移动成功");
    }

    /**
     * 批量排序：按传入 id 顺序重排 sort 值（0..n），用于脑图兄弟节点顺序持久化
     */
    @Override
    public SaResult sortCases(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return SaResult.ok();
        }
        for (int i = 0; i < ids.size(); i++) {
            TestCase tc = new TestCase();
            tc.setId(ids.get(i));
            tc.setSort(i);
            baseMapper.updateById(tc);
        }
        return SaResult.ok("排序已保存");
    }

    @Override
    public SaResult getDetail(Integer id) {
        if (id == null) {
            return SaResult.error("缺少ID");
        }
        TestCase testCase = getById(id);
        if (testCase == null) {
            return SaResult.error("用例不存在");
        }
        SaResult setResult = testCaseSetService.listByCaseId(id);
        @SuppressWarnings("unchecked")
        List<com.mokatest.platform.demos.qa.domain.TestCaseSet> sets = (List<com.mokatest.platform.demos.qa.domain.TestCaseSet>) setResult.getData();
        if (sets != null && !sets.isEmpty()) {
            testCase.setSetIds(sets.stream().map(com.mokatest.platform.demos.qa.domain.TestCaseSet::getId).collect(Collectors.toList()));
        }
        // 补充创建人/更新人/所属模块名称（实体非表字段，仅展示用）
        Set<Integer> userIds = new HashSet<>();
        if (testCase.getCreateUserId() != null) userIds.add(testCase.getCreateUserId());
        if (testCase.getUpdateUserId() != null) userIds.add(testCase.getUpdateUserId());
        if (!userIds.isEmpty()) {
            Map<Integer, String> userMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(u -> u.getId().intValue(), User::getUsername, (a, b) -> a));
            testCase.setCreateUserName(userMap.getOrDefault(testCase.getCreateUserId(), ""));
            testCase.setUpdateUserName(userMap.getOrDefault(testCase.getUpdateUserId(), ""));
        }
        if (testCase.getModuleId() != null) {
            com.mokatest.platform.demos.qa.domain.QaModule module = qaModuleMapper.selectById(testCase.getModuleId());
            if (module != null) {
                testCase.setModuleName(module.getModuleName());
            }
        }
        return SaResult.ok().setData(testCase);
    }

    @Override
    public SaResult transitionStatus(Integer testCaseId, String targetStatus, String comment) {
        if (testCaseId == null) {
            return SaResult.error("缺少用例ID");
        }
        if (targetStatus == null || !VALID_STATUSES.contains(targetStatus)) {
            return SaResult.error("无效的目标状态: " + targetStatus);
        }
        TestCase testCase = getById(testCaseId);
        if (testCase == null) {
            return SaResult.error("用例不存在");
        }
        String oldStatus = testCase.getStatus();
        // 评审中打回草稿时意见必填
        if ("DRAFT".equals(targetStatus) && "REVIEWING".equals(oldStatus)
                && (comment == null || comment.trim().isEmpty())) {
            return SaResult.error("打回草稿时意见必填");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        testCase.setStatus(targetStatus);
        testCase.setUpdateUserId(loginId);
        testCase.setUpdateTime(new Date());
        boolean success = updateById(testCase);
        if (success) {
            testCaseOperationLogService.log(testCaseId, "TRANSITION", "status", oldStatus, targetStatus, comment);
            sendCaseStatusChangedMessage(testCase, oldStatus, targetStatus, loginId);
        }
        return success ? SaResult.ok("状态已更新为: " + targetStatus) : SaResult.error("状态更新失败");
    }

    @Override
    public void exportExcel(Integer projectId, Integer moduleId, Integer setId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        QueryWrapper<TestCase> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        if (moduleId != null) {
            Set<Integer> moduleIds = collectDescendantModuleIds(projectId, moduleId);
            if (moduleIds.isEmpty()) {
                moduleIds = Collections.singleton(moduleId);
            }
            wrapper.in("module_id", moduleIds);
        }
        if (setId != null) {
            List<Integer> caseIds = testCaseSetService.getCaseIdsBySetId(setId);
            if (!caseIds.isEmpty()) {
                wrapper.in("id", caseIds);
            }
        }
        wrapper.orderByDesc("create_time");
        List<TestCase> list = baseMapper.selectList(wrapper);

        // 准备Excel数据
        List<List<Object>> rows = new ArrayList<>();
        // 表头
        List<Object> header = Arrays.asList("用例编号", "用例名称", "类型", "优先级", "状态", "前置条件", "测试步骤", "关联需求ID", "所属模块ID", "创建时间");
        rows.add(header);

        // 数据行
        for (TestCase tc : list) {
            List<Object> row = new ArrayList<>();
            row.add(tc.getCaseCode());
            row.add(tc.getCaseName());
            row.add(tc.getCaseType());
            row.add(tc.getPriority());
            row.add(tc.getStatus());
            row.add(tc.getPreCondition());
            // 测试步骤转为JSON字符串
            row.add(tc.getTestSteps() != null ? com.alibaba.fastjson.JSON.toJSONString(tc.getTestSteps()) : "");
            row.add(tc.getRequirementId());
            row.add(tc.getModuleId());
            row.add(tc.getCreateTime() != null ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(tc.getCreateTime()) : "");
            rows.add(row);
        }

        // 使用Hutool导出Excel
        cn.hutool.poi.excel.ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter();
        writer.write(rows, true);
        writer.autoSizeColumnAll();

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=test_case_export.xlsx");
        writer.flush(response.getOutputStream());
        writer.close();
    }

    private Set<Integer> collectDescendantModuleIds(Integer projectId, Integer moduleId) {
        QueryWrapper<com.mokatest.platform.demos.qa.domain.QaModule> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        List<com.mokatest.platform.demos.qa.domain.QaModule> modules = qaModuleMapper.selectList(wrapper);
        Map<Integer, List<Integer>> parentMap = new HashMap<>();
        for (com.mokatest.platform.demos.qa.domain.QaModule module : modules) {
            Integer pid = module.getParentId() != null ? module.getParentId() : 0;
            parentMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(module.getId());
        }
        Set<Integer> result = new HashSet<>();
        result.add(moduleId);
        collectDescendantIdsRecursive(parentMap, moduleId, result);
        return result;
    }

    private void collectDescendantIdsRecursive(Map<Integer, List<Integer>> parentMap, Integer parentId, Set<Integer> result) {
        List<Integer> children = parentMap.getOrDefault(parentId, Collections.emptyList());
        for (Integer childId : children) {
            result.add(childId);
            collectDescendantIdsRecursive(parentMap, childId, result);
        }
    }

    private static final java.util.Set<String> VALID_STATUSES = new java.util.HashSet<>(java.util.Arrays.asList(
            "DRAFT", "REVIEWING", "REVIEWED", "DEPRECATED"
    ));

    @Override
    public SaResult stats(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("total", baseMapper.selectCount(new QueryWrapper<TestCase>().eq("project_id", projectId)));
        map.put("pass", baseMapper.selectCount(new QueryWrapper<TestCase>().eq("project_id", projectId).eq("last_result", "PASS")));
        map.put("fail", baseMapper.selectCount(new QueryWrapper<TestCase>().eq("project_id", projectId).eq("last_result", "FAIL")));
        map.put("unexec", baseMapper.selectCount(new QueryWrapper<TestCase>().eq("project_id", projectId)
                .and(w -> w.isNull("last_result").or().eq("last_result", ""))));
        return SaResult.data(map);
    }

    /**
     * 发送用例创建通知
     */
    private void sendCaseCreatedMessage(TestCase testCase, Integer loginId) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("caseCode", testCase.getCaseCode());
        params.put("caseName", testCase.getCaseName());
        params.put("operatorName", getOperatorName(loginId));
        params.put("projectName", getProjectName(testCase.getProjectId()));
        params.put("moduleName", getModuleName(testCase.getModuleId()));
        Map<String, Object> snapshot = buildCaseSnapshot(testCase);
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        if (testCase.getCreateUserId() != null) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.CASE_CREATED)
                    .senderId(loginId)
                    .receiverId(testCase.getCreateUserId())
                    .receiverRole("CREATOR")
                    .projectId(testCase.getProjectId())
                    .teamId(getTeamIdByProjectId(testCase.getProjectId()))
                    .bizType("testCase")
                    .bizId(testCase.getId())
                    .params(params)
                    .snapshot(snapshot)
                    .build());
        }
        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    /**
     * 发送用例状态变更通知
     */
    private void sendCaseStatusChangedMessage(TestCase testCase, String oldStatus, String newStatus, Integer loginId) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("caseCode", testCase.getCaseCode());
        params.put("caseName", testCase.getCaseName());
        params.put("operatorName", getOperatorName(loginId));
        params.put("oldStatus", oldStatus);
        params.put("newStatus", newStatus);
        params.put("projectName", getProjectName(testCase.getProjectId()));
        params.put("moduleName", getModuleName(testCase.getModuleId()));
        Map<String, Object> snapshot = buildCaseSnapshot(testCase);
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        if (testCase.getCreateUserId() != null) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.CASE_STATUS_CHANGED)
                    .senderId(loginId)
                    .receiverId(testCase.getCreateUserId())
                    .receiverRole("CREATOR")
                    .projectId(testCase.getProjectId())
                    .teamId(getTeamIdByProjectId(testCase.getProjectId()))
                    .bizType("testCase")
                    .bizId(testCase.getId())
                    .params(params)
                    .snapshot(snapshot)
                    .build());
        }
        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    /**
     * 发送用例更新通知
     */
    private void sendCaseUpdatedMessage(TestCase testCase, Integer loginId) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("caseCode", testCase.getCaseCode());
        params.put("caseName", testCase.getCaseName());
        params.put("operatorName", getOperatorName(loginId));
        params.put("projectName", getProjectName(testCase.getProjectId()));
        params.put("moduleName", getModuleName(testCase.getModuleId()));
        Map<String, Object> snapshot = buildCaseSnapshot(testCase);
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        if (testCase.getCreateUserId() != null) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.CASE_UPDATED)
                    .senderId(loginId)
                    .receiverId(testCase.getCreateUserId())
                    .receiverRole("CREATOR")
                    .projectId(testCase.getProjectId())
                    .teamId(getTeamIdByProjectId(testCase.getProjectId()))
                    .bizType("testCase")
                    .bizId(testCase.getId())
                    .params(params)
                    .snapshot(snapshot)
                    .build());
        }
        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    /**
     * 发送用例删除通知
     */
    private void sendCaseDeletedMessage(TestCase testCase, Integer loginId) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("caseCode", testCase.getCaseCode());
        params.put("caseName", testCase.getCaseName());
        params.put("operatorName", getOperatorName(loginId));
        params.put("projectName", getProjectName(testCase.getProjectId()));
        params.put("moduleName", getModuleName(testCase.getModuleId()));
        params.put("operateTime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        Map<String, Object> snapshot = buildCaseSnapshot(testCase);
        java.util.List<MessageContext> contexts = new java.util.ArrayList<>();
        if (testCase.getCreateUserId() != null) {
            contexts.add(MessageContext.builder()
                    .eventType(MessageEventType.CASE_DELETED)
                    .senderId(loginId)
                    .receiverId(testCase.getCreateUserId())
                    .receiverRole("CREATOR")
                    .projectId(testCase.getProjectId())
                    .teamId(getTeamIdByProjectId(testCase.getProjectId()))
                    .bizType("testCase")
                    .bizId(testCase.getId())
                    .params(params)
                    .snapshot(snapshot)
                    .build());
        }
        if (!contexts.isEmpty()) {
            messageService.sendBatch(contexts);
        }
    }

    /**
     * 构建用例快照（用于消息详情展示）
     */
    private Map<String, Object> buildCaseSnapshot(TestCase testCase) {
        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("caseCode", testCase.getCaseCode());
        snapshot.put("caseName", testCase.getCaseName());
        snapshot.put("projectId", testCase.getProjectId());
        return snapshot;
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

}
