package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.qa.domain.QaModule;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.TestPlan;
import com.mokatest.platform.demos.qa.mapper.BugMapper;
import com.mokatest.platform.demos.qa.mapper.QaModuleMapper;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import com.mokatest.platform.demos.qa.mapper.TestPlanMapper;
import com.mokatest.platform.demos.qa.service.QaModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QaModuleServiceImpl extends ServiceImpl<QaModuleMapper, QaModule> implements QaModuleService {

    private final QaModuleMapper qaModuleMapper;
    private final RequirementMapper requirementMapper;
    private final BugMapper bugMapper;
    private final TestCaseMapper testCaseMapper;
    private final TestPlanMapper testPlanMapper;
    private final com.mokatest.platform.demos.qa.mapper.TestCaseReviewMapper testCaseReviewMapper;

    @Override
    public SaResult listByProject(Integer projectId, String moduleType) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        String type = (moduleType == null || moduleType.isEmpty()) ? "CASE" : moduleType;
        QueryWrapper<QaModule> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId).eq("module_type", type).orderByAsc("sort", "id");
        List<QaModule> list = baseMapper.selectList(wrapper);

        // 填充每个模块下的直接归属数量（CASE 类型=用例数，REVIEW 类型=评审单数）
        Map<Integer, Long> caseCountMap = countByModuleType(projectId, type);
        for (QaModule module : list) {
            module.setCaseCount(caseCountMap.getOrDefault(module.getId(), 0L));
        }
        // 全量总数（含未选模块的记录），供前端虚拟根节点「全部」展示
        return SaResult.ok().setData(list).set("totalCount", totalByModuleType(projectId, type));
    }

    /**
     * 按 module_id 统计项目下未删除用例数量
     */
    private Map<Integer, Long> countCaseByModule(Integer projectId) {
        QueryWrapper<TestCase> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId)
                .eq("is_deleted", 0)
                .isNotNull("module_id")
                .groupBy("module_id")
                .select("module_id", "count(*) as cnt");
        List<Map<String, Object>> maps = testCaseMapper.selectMaps(wrapper);
        Map<Integer, Long> result = new HashMap<>();
        for (Map<String, Object> map : maps) {
            Integer moduleId = (Integer) map.get("module_id");
            Long count = ((Number) map.get("cnt")).longValue();
            result.put(moduleId, count);
        }
        return result;
    }

    /**
     * 按模块类型统计数量：CASE=用例数，REVIEW=评审单数
     */
    private Map<Integer, Long> countByModuleType(Integer projectId, String type) {
        if ("REVIEW".equals(type)) {
            return countReviewByModule(projectId);
        }
        if ("CASE".equals(type)) {
            return countCaseByModule(projectId);
        }
        if ("PLAN".equals(type)) {
            return countPlanByModule(projectId);
        }
        return new HashMap<>();
    }

    private Map<Integer, Long> countPlanByModule(Integer projectId) {
        QueryWrapper<TestPlan> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId)
                .eq("is_deleted", 0)
                .isNotNull("module_id")
                .groupBy("module_id")
                .select("module_id", "count(*) as cnt");
        List<Map<String, Object>> maps = testPlanMapper.selectMaps(wrapper);
        Map<Integer, Long> result = new HashMap<>();
        for (Map<String, Object> map : maps) {
            Integer moduleId = (Integer) map.get("module_id");
            Long count = ((Number) map.get("cnt")).longValue();
            result.put(moduleId, count);
        }
        return result;
    }

    /**
     * 按 module_id 统计项目下未删除评审单数量
     */
    private Map<Integer, Long> countReviewByModule(Integer projectId) {
        QueryWrapper<com.mokatest.platform.demos.qa.domain.TestCaseReview> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId)
                .eq("is_deleted", 0)
                .isNotNull("module_id")
                .groupBy("module_id")
                .select("module_id", "count(*) as cnt");
        List<Map<String, Object>> maps = testCaseReviewMapper.selectMaps(wrapper);
        Map<Integer, Long> result = new HashMap<>();
        for (Map<String, Object> map : maps) {
            Integer moduleId = (Integer) map.get("module_id");
            Long count = ((Number) map.get("cnt")).longValue();
            result.put(moduleId, count);
        }
        return result;
    }

    @Override
    public SaResult tree(Integer projectId, String moduleType) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        String type = (moduleType == null || moduleType.isEmpty()) ? "CASE" : moduleType;
        QueryWrapper<QaModule> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId).eq("module_type", type).orderByAsc("sort", "id");
        List<QaModule> list = baseMapper.selectList(wrapper);

        Map<Integer, List<QaModule>> parentMap = new HashMap<>();
        for (QaModule module : list) {
            Integer pid = module.getParentId() != null ? module.getParentId() : 0;
            parentMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(module);
        }

        // 填充数量，便于前端展示（CASE=用例数，REVIEW=评审单数）
        Map<Integer, Long> caseCountMap = countByModuleType(projectId, type);
        List<Map<String, Object>> tree = buildTreeNodes(parentMap, 0, caseCountMap);

        Map<String, Object> root = new HashMap<>();
        root.put("id", 0);
        String rootName = "CASE".equals(type) ? "全部用例" : "REVIEW".equals(type) ? "全部评审" : "全部计划";
        root.put("moduleName", rootName);
        root.put("parentId", 0);
        // 根节点统计全量（含未选择模块、module_id 为空的记录），而非仅各模块之和
        root.put("caseCount", totalByModuleType(projectId, type));
        root.put("children", tree);

        return SaResult.ok().setData(List.of(root));
    }

    /**
     * 项目下该类型的全量数量（不限模块归属）：CASE=用例数，REVIEW=评审单数
     */
    private long totalByModuleType(Integer projectId, String type) {
        if ("REVIEW".equals(type)) {
            QueryWrapper<com.mokatest.platform.demos.qa.domain.TestCaseReview> wrapper = new QueryWrapper<>();
            wrapper.eq("project_id", projectId).eq("is_deleted", 0);
            return testCaseReviewMapper.selectCount(wrapper);
        }
        if ("CASE".equals(type)) {
            QueryWrapper<TestCase> wrapper = new QueryWrapper<>();
            wrapper.eq("project_id", projectId).eq("is_deleted", 0);
            return testCaseMapper.selectCount(wrapper);
        }
        if ("PLAN".equals(type)) {
            QueryWrapper<TestPlan> wrapper = new QueryWrapper<>();
            wrapper.eq("project_id", projectId).eq("is_deleted", 0);
            return testPlanMapper.selectCount(wrapper);
        }
        return 0L;
    }

    private List<Map<String, Object>> buildTreeNodes(Map<Integer, List<QaModule>> parentMap, Integer parentId, Map<Integer, Long> caseCountMap) {
        List<QaModule> children = parentMap.getOrDefault(parentId, new ArrayList<>());
        List<Map<String, Object>> result = new ArrayList<>();
        for (QaModule module : children) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", module.getId());
            node.put("moduleName", module.getModuleName());
            node.put("parentId", module.getParentId());
            node.put("sort", module.getSort());
            node.put("caseCount", caseCountMap.getOrDefault(module.getId(), 0L));
            node.put("children", buildTreeNodes(parentMap, module.getId(), caseCountMap));
            result.add(node);
        }
        return result;
    }

    @Override
    public SaResult saveOrUpdateModule(QaModule module) {
        if (module == null) {
            return SaResult.error("缺少参数");
        }
        if (module.getProjectId() == null) {
            return SaResult.error("缺少项目ID");
        }
        if (module.getModuleName() == null || module.getModuleName().trim().isEmpty()) {
            return SaResult.error("模块名称不能为空");
        }
        if (module.getParentId() == null) {
            module.setParentId(0);
        }
        if (module.getModuleType() == null || module.getModuleType().isEmpty()) {
            module.setModuleType("CASE");
        }

        // sort 为空时，自动放到所属父模块的末尾（仅新建；更新时 sort 为 null 表示不调整顺序，
        // 否则 MyBatis-Plus saveOrUpdate 会把重算后的 sort 写回，改名/移动操作会意外打乱同级顺序）
        if (module.getId() == null && module.getSort() == null) {
            QueryWrapper<QaModule> sortWrapper = new QueryWrapper<>();
            sortWrapper.eq("project_id", module.getProjectId())
                    .eq("parent_id", module.getParentId())
                    .orderByDesc("sort");
            QaModule maxSortModule = qaModuleMapper.selectOne(sortWrapper.last("limit 1"));
            module.setSort(maxSortModule != null && maxSortModule.getSort() != null ? maxSortModule.getSort() + 1 : 0);
        }

        boolean success = saveOrUpdate(module);
        return success ? SaResult.ok("保存成功").setData(module.getId()) : SaResult.error("保存失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult deleteModule(Integer id) {
        if (id == null) {
            return SaResult.error("缺少模块ID");
        }
        QaModule module = qaModuleMapper.selectById(id);
        if (module == null) {
            return SaResult.ok("模块已删除");
        }

        // 1. 解绑关联需求的 module_id
        UpdateWrapper<com.mokatest.platform.demos.qa.domain.Requirement> reqWrapper = new UpdateWrapper<>();
        reqWrapper.eq("module_id", id).set("module_id", null);
        requirementMapper.update(null, reqWrapper);

        // 2. 解绑关联 BUG 的 module_id
        UpdateWrapper<com.mokatest.platform.demos.qa.domain.Bug> bugWrapper = new UpdateWrapper<>();
        bugWrapper.eq("module_id", id).set("module_id", null);
        bugMapper.update(null, bugWrapper);

        // 3. 解绑关联用例的 module_id
        UpdateWrapper<com.mokatest.platform.demos.qa.domain.TestCase> caseWrapper = new UpdateWrapper<>();
        caseWrapper.eq("module_id", id).set("module_id", null);
        testCaseMapper.update(null, caseWrapper);

        // 3.5 解绑关联测试计划的 module_id
        UpdateWrapper<TestPlan> planWrapper = new UpdateWrapper<>();
        planWrapper.eq("module_id", id).set("module_id", null);
        testPlanMapper.update(null, planWrapper);

        // 4. 子模块变为根模块
        UpdateWrapper<QaModule> childWrapper = new UpdateWrapper<>();
        childWrapper.eq("parent_id", id).set("parent_id", 0);
        qaModuleMapper.update(null, childWrapper);

        // 5. 逻辑删除模块
        module.setDeletedAt(new Date());
        boolean success = removeById(module);
        return success ? SaResult.ok("删除成功") : SaResult.error("删除失败");
    }

    @Override
    public SaResult sortModule(Integer moduleId, Integer targetParentId, Integer targetIndex) {
        if (moduleId == null) {
            return SaResult.error("缺少模块ID");
        }
        QaModule module = qaModuleMapper.selectById(moduleId);
        if (module == null) {
            return SaResult.error("模块不存在");
        }
        if (targetParentId == null) {
            targetParentId = 0;
        }
        if (targetIndex == null || targetIndex < 0) {
            targetIndex = 0;
        }

        Integer projectId = module.getProjectId();

        // 不能把自己拖到自己内部（避免循环引用）
        if (targetParentId.equals(moduleId)) {
            return SaResult.error("不能将模块移动到自身内部");
        }
        // 也不能把模块拖到它的子模块下
        if (isDescendant(moduleId, targetParentId, projectId, module.getModuleType())) {
            return SaResult.error("不能将模块移动到其子模块下");
        }

        // 1. 查询目标父节点下的所有子节点（排除被拖动的节点），按 sort 排序
        QueryWrapper<QaModule> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId)
                .eq("parent_id", targetParentId)
                .eq("module_type", module.getModuleType())
                .ne("id", moduleId)
                .orderByAsc("sort", "id");
        List<QaModule> siblings = list(wrapper);

        // 2. 在目标位置插入被拖动的节点
        if (targetIndex > siblings.size()) {
            targetIndex = siblings.size();
        }
        siblings.add(targetIndex, module);

        // 3. 重新分配 sort（0, 1, 2...），批量更新
        for (int i = 0; i < siblings.size(); i++) {
            QaModule m = siblings.get(i);
            int newSort = i;
            boolean parentChanged = !targetParentId.equals(m.getParentId());
            boolean sortChanged = m.getSort() == null || m.getSort() != newSort;
            if (parentChanged || sortChanged) {
                m.setParentId(targetParentId);
                m.setSort(newSort);
                m.setUpdateTime(new Date());
                updateById(m);
            }
        }

        return SaResult.ok("排序完成");
    }

    /**
     * 判断 targetId 是否是 moduleId 的后代节点
     */
    private boolean isDescendant(Integer moduleId, Integer targetId, Integer projectId, String moduleType) {
        if (targetId == null || targetId == 0) {
            return false;
        }
        QueryWrapper<QaModule> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId).eq("module_type", moduleType);
        List<QaModule> all = list(wrapper);
        Map<Integer, Integer> parentMap = new HashMap<>();
        for (QaModule m : all) {
            parentMap.put(m.getId(), m.getParentId() != null ? m.getParentId() : 0);
        }
        Integer current = targetId;
        while (current != null && current != 0) {
            Integer parent = parentMap.get(current);
            if (moduleId.equals(parent)) {
                return true;
            }
            current = parent;
        }
        return false;
    }
}
