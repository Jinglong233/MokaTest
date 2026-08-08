package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.vo.TestCaseVO;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
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
    private final com.mokatest.platform.demos.qa.mapper.TestCaseAutoBindMapper testCaseAutoBindMapper;
    private final com.mokatest.platform.demos.qa.service.TestCaseSetService testCaseSetService;
    private final com.mokatest.platform.demos.qa.mapper.TestCaseSetRelationMapper testCaseSetRelationMapper;

    @Override
    public SaResult listByProject(Integer projectId, Integer moduleId, Integer setId, Integer requirementId, String keyword, String lastResult, Integer page, Integer pageSize, Integer excludePlanId) {
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
            wrapper.eq("requirement_id", requirementId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("case_name", keyword).or().like("case_code", keyword));
        }
        if (lastResult != null && !lastResult.isEmpty()) {
            wrapper.eq("last_result", lastResult);
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
        wrapper.orderByDesc("create_time");

        IPage<TestCase> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        IPage<TestCase> result = baseMapper.selectPage(pageParam, wrapper);

        Map<String, Object> data = new HashMap<>();
        // 补充创建人名称和模块名称
        List<TestCase> records = result.getRecords();
        if (!records.isEmpty()) {
            Set<Integer> userIds = records.stream()
                    .map(TestCase::getCreateUserId)
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

            // 查询需求标题
            Set<Integer> reqIds = records.stream()
                    .map(TestCase::getRequirementId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Integer, String> reqMap = new HashMap<>();
            if (!reqIds.isEmpty()) {
                List<com.mokatest.platform.demos.qa.domain.Requirement> reqs = requirementMapper.selectBatchIds(reqIds);
                reqMap = reqs.stream()
                        .collect(Collectors.toMap(com.mokatest.platform.demos.qa.domain.Requirement::getId, com.mokatest.platform.demos.qa.domain.Requirement::getTitle, (a, b) -> a));
            }

            // 查询关联BUG数量
            List<Integer> caseIds = records.stream().map(TestCase::getId).collect(Collectors.toList());
            Map<Integer, Long> bugCountMap = new HashMap<>();
            if (!caseIds.isEmpty()) {
                QueryWrapper<com.mokatest.platform.demos.qa.domain.Bug> bugWrapper = new QueryWrapper<>();
                bugWrapper.in("test_case_id", caseIds);
                List<com.mokatest.platform.demos.qa.domain.Bug> bugs = bugMapper.selectList(bugWrapper);
                bugCountMap = bugs.stream()
                        .collect(Collectors.groupingBy(com.mokatest.platform.demos.qa.domain.Bug::getTestCaseId, Collectors.counting()));
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
                vo.setBugCount(bugCountMap.getOrDefault(tc.getId(), 0L));
                vo.setCreateUserId(tc.getCreateUserId());
                vo.setCreateUserName(userMap.getOrDefault(tc.getCreateUserId(), ""));
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
            wrapper.eq("requirement_id", requirementId);
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
        if (testCase.getCaseName() == null || testCase.getCaseName().trim().isEmpty()) {
            return SaResult.error("用例名称不能为空");
        }

        boolean isNew = testCase.getId() == null;
        if (isNew && (testCase.getCaseCode() == null || testCase.getCaseCode().trim().isEmpty())) {
            // 先占一个临时唯一值，等插入拿到自增 id 后再改成 CASE-{id}
            testCase.setCaseCode("TMP-" + System.nanoTime());
        }
        if (testCase.getCaseType() == null) {
            testCase.setCaseType("FUNCTION");
        }
        if (testCase.getPriority() == null) {
            testCase.setPriority("P1");
        }
        if (testCase.getStatus() == null) {
            testCase.setStatus("DRAFT");
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

        boolean success = saveOrUpdate(testCase);
        if (!success) {
            return SaResult.error("保存失败");
        }
        // 新增用例时，把临时编号改为短格式 CASE-{id}
        if (isNew && testCase.getCaseCode() != null && testCase.getCaseCode().startsWith("TMP-")) {
            testCase.setCaseCode("CASE-" + testCase.getId());
            updateById(testCase);
        }

        // 同步测试集关联
        if (setIds != null) {
            testCaseSetService.bindSets(testCase.getId(), setIds);
        }

        return SaResult.ok().setData(testCase.getId());
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
        // 1. 解绑关联 BUG 的 test_case_id
        UpdateWrapper<com.mokatest.platform.demos.qa.domain.Bug> bugUpdateWrapper = new UpdateWrapper<>();
        bugUpdateWrapper.eq("test_case_id", id).set("test_case_id", null);
        bugMapper.update(null, bugUpdateWrapper);

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

        // 5. 逻辑删除用例本身
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
        // 1. 解绑关联 BUG
        UpdateWrapper<com.mokatest.platform.demos.qa.domain.Bug> bugUpdateWrapper = new UpdateWrapper<>();
        bugUpdateWrapper.in("test_case_id", ids).set("test_case_id", null);
        bugMapper.update(null, bugUpdateWrapper);

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

        // 5. 批量逻辑删除用例（逐条设置 deleted_at）
        List<TestCase> cases = baseMapper.selectBatchIds(ids);
        Date now = new Date();
        for (TestCase testCase : cases) {
            if (testCase != null) {
                testCase.setDeletedAt(now);
                baseMapper.deleteById(testCase);
            }
        }
        return SaResult.ok("批量删除成功");
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
        return SaResult.ok().setData(testCase);
    }

    @Override
    public SaResult transitionStatus(Integer testCaseId, String targetStatus) {
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
        testCase.setStatus(targetStatus);
        testCase.setUpdateUserId(StpUtil.getLoginIdAsInt());
        testCase.setUpdateTime(new Date());
        boolean success = updateById(testCase);
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

}
