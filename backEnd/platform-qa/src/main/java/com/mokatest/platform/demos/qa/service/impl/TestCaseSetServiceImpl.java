package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.qa.domain.TestCaseSet;
import com.mokatest.platform.demos.qa.domain.TestCaseSetRelation;
import com.mokatest.platform.demos.qa.mapper.TestCaseSetMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseSetRelationMapper;
import com.mokatest.platform.demos.qa.service.TestCaseSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 测试集 Service 实现
 */
@Service
@RequiredArgsConstructor
public class TestCaseSetServiceImpl extends ServiceImpl<TestCaseSetMapper, TestCaseSet> implements TestCaseSetService {

    private final TestCaseSetRelationMapper relationMapper;

    @Override
    public SaResult saveOrUpdateSet(TestCaseSet set) {
        if (set == null) {
            return SaResult.error("缺少参数");
        }
        if (set.getProjectId() == null) {
            return SaResult.error("缺少项目ID");
        }
        if (set.getSetName() == null || set.getSetName().trim().isEmpty()) {
            return SaResult.error("测试集名称不能为空");
        }
        if (set.getSort() == null) {
            set.setSort(0);
        }

        Integer loginId = StpUtil.getLoginIdAsInt();
        if (set.getId() == null) {
            set.setCreateUserId(loginId);
            set.setCreateTime(new Date());
        }
        set.setUpdateUserId(loginId);
        set.setUpdateTime(new Date());

        boolean success = saveOrUpdate(set);
        return success ? SaResult.ok("保存成功").setData(set.getId()) : SaResult.error("保存失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult deleteSet(Integer id) {
        if (id == null) {
            return SaResult.error("缺少测试集ID");
        }
        TestCaseSet set = getById(id);
        if (set == null) {
            return SaResult.ok("测试集已删除");
        }
        // 物理清理关联关系
        QueryWrapper<TestCaseSetRelation> relationWrapper = new QueryWrapper<>();
        relationWrapper.eq("set_id", id);
        relationMapper.delete(relationWrapper);
        // 逻辑删除测试集
        set.setDeletedAt(new Date());
        boolean success = removeById(set);
        return success ? SaResult.ok("删除成功") : SaResult.error("删除失败");
    }

    @Override
    public SaResult listByProject(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        QueryWrapper<TestCaseSet> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId).orderByAsc("sort", "id");
        List<TestCaseSet> list = baseMapper.selectList(wrapper);
        return SaResult.ok().setData(list);
    }

    @Override
    public SaResult listOptions(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        QueryWrapper<TestCaseSet> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId).orderByAsc("sort", "id");
        List<TestCaseSet> list = baseMapper.selectList(wrapper);
        List<Map<String, Object>> options = new ArrayList<>();
        for (TestCaseSet set : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", set.getId());
            item.put("setName", set.getSetName());
            options.add(item);
        }
        return SaResult.ok().setData(options);
    }

    @Override
    public SaResult listByCaseId(Integer caseId) {
        if (caseId == null) {
            return SaResult.error("缺少用例ID");
        }
        QueryWrapper<TestCaseSetRelation> wrapper = new QueryWrapper<>();
        wrapper.eq("test_case_id", caseId);
        List<TestCaseSetRelation> relations = relationMapper.selectList(wrapper);
        List<Integer> setIds = relations.stream()
                .map(TestCaseSetRelation::getSetId)
                .distinct()
                .collect(Collectors.toList());
        if (setIds.isEmpty()) {
            return SaResult.ok().setData(Collections.emptyList());
        }
        List<TestCaseSet> sets = baseMapper.selectBatchIds(setIds);
        return SaResult.ok().setData(sets);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult bindSets(Integer caseId, List<Integer> setIds) {
        if (caseId == null) {
            return SaResult.error("缺少用例ID");
        }
        // 清理旧关联
        QueryWrapper<TestCaseSetRelation> wrapper = new QueryWrapper<>();
        wrapper.eq("test_case_id", caseId);
        relationMapper.delete(wrapper);
        // 写入新关联
        if (setIds != null && !setIds.isEmpty()) {
            List<Integer> distinctIds = setIds.stream().distinct().collect(Collectors.toList());
            // 校验测试集是否存在且未删除
            List<TestCaseSet> existingSets = baseMapper.selectBatchIds(distinctIds);
            Set<Integer> validIds = existingSets.stream()
                    .map(TestCaseSet::getId)
                    .collect(Collectors.toSet());
            for (Integer setId : distinctIds) {
                if (!validIds.contains(setId)) {
                    continue;
                }
                TestCaseSetRelation relation = new TestCaseSetRelation();
                relation.setSetId(setId);
                relation.setTestCaseId(caseId);
                relation.setCreateTime(new Date());
                relationMapper.insert(relation);
            }
        }
        return SaResult.ok();
    }

    @Override
    public List<Integer> getCaseIdsBySetId(Integer setId) {
        if (setId == null) {
            return Collections.emptyList();
        }
        QueryWrapper<TestCaseSetRelation> wrapper = new QueryWrapper<>();
        wrapper.eq("set_id", setId);
        List<TestCaseSetRelation> relations = relationMapper.selectList(wrapper);
        return relations.stream()
                .map(TestCaseSetRelation::getTestCaseId)
                .distinct()
                .collect(Collectors.toList());
    }
}
