package com.mokatest.platform.demos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.other.AddAdjacentStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.other.ImportExistSceneStepDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.DebuggerState;
import com.mokatest.platform.demos.domain.ui.vo.StepVO;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.exception.ParamIsEmptyException;
import com.mokatest.platform.demos.manager.DebugSessionManager;
import com.mokatest.platform.demos.mapper.SceneMapper;
import com.mokatest.platform.demos.service.TestStepService;
import com.mokatest.platform.demos.mapper.TestStepMapper;
import jakarta.annotation.Resource;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author: JingLong
 * @description 针对表【test_step(测试步骤主表)】的数据库操作Service实现
 * @createDate 2025-07-26 11:10:33
 */
@Service
public class TestStepServiceImpl extends ServiceImpl<TestStepMapper, TestStep> implements TestStepService {

    @Resource
    private TestStepMapper testStepMapper;

    @Resource
    private SceneMapper sceneMapper;

    @Resource
    private DebugSessionManager debugSessionManager;

    /**
     * 调试运行中写拦截：场景处于调试运行（RUNNING/STEPPING）状态时禁止步骤写操作。
     * 暂停/失败挂起状态放行，用户续跑时会热加载最新步骤。
     *
     * @param scenarioId 场景id（String/Integer 均可），为空或非法时直接放行
     */
    private void checkSceneDebugRunning(Object scenarioId) {
        if (scenarioId == null) {
            return;
        }
        Integer sceneId;
        try {
            sceneId = Integer.valueOf(scenarioId.toString());
        } catch (NumberFormatException e) {
            return;
        }
        DebuggerState state = debugSessionManager.getDebugStateBySceneId(sceneId);
        if (state == DebuggerState.RUNNING || state == DebuggerState.STEPPING) {
            throw new BusinessException("场景正在调试运行中，请先暂停后再修改步骤");
        }
    }


    @Override
    public List<StepVO> getStepList(Integer sceneId) {
        if (sceneId == null) {
            throw new ParamIsEmptyException("缺少场景id");
        }
        Scene scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new RuntimeException("场景不存在");
        }

        // 获取场景列表
        QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("scenario_id", sceneId);
        List<TestStep> testSteps = testStepMapper.selectList(queryWrapper);
        if (testSteps.isEmpty()) return new ArrayList<>();
        // 获取所有的父节点
        List<TestStep> parentStep = testSteps.stream().filter(testStep -> testStep.getParentId() == 0).toList();
        List<StepVO> result = new ArrayList<>();
        for (TestStep testStep : parentStep) {
            StepVO stepVO = new StepVO();
            stepVO.setChildren(new ArrayList<>());
            BeanUtils.copyProperties(testStep, stepVO);
            buildChildrenStepNode(testSteps, stepVO);
            result.add(stepVO);
        }
        // 排序
        result.sort(Comparator.comparing(StepVO::getOrderIndex));
        return result;
    }

    private void buildChildrenStepNode(List<TestStep> testSteps, StepVO parentStep) {
        // 如果没有子节点就直接return;
        Integer parentId = parentStep.getId();
        List<TestStep> childrenStep =
                testSteps.stream().filter(testStep -> testStep.getParentId().equals(parentId)).toList();
        if (childrenStep.isEmpty()) {
            return;
        }
        for (TestStep step : childrenStep) {
            StepVO stepVO = new StepVO();
            stepVO.setChildren(new ArrayList<>());
            BeanUtils.copyProperties(step, stepVO);
            buildChildrenStepNode(testSteps, stepVO);
            parentStep.getChildren().add(stepVO);
        }
        // 排序
        parentStep.getChildren().sort(Comparator.comparing(StepVO::getOrderIndex));
    }

    @Override
    public TestStep getStepDetail(Integer stepId) {
        if (stepId == null) {
            throw new ParamIsEmptyException("缺少步骤id");
        }
        QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", stepId);
        TestStep testStep = testStepMapper.selectOne(queryWrapper);
        if (testStep == null) {
            throw new RuntimeException("步骤不存在");
        }
        return testStep;
    }

    @Override
    public Boolean addStep(TestStep step) {
        if (step == null) {
            throw new ParamIsEmptyException("缺少步骤信息");
        }
        checkSceneDebugRunning(step.getScenarioId());
        // 设置步骤索引
        QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("scenario_id", step.getScenarioId());
        queryWrapper.eq("parent_id", step.getParentId());
        List<TestStep> testSteps = testStepMapper.selectList(queryWrapper);
        if (testSteps.isEmpty()) {
            step.setOrderIndex(1);
        } else {
            step.setOrderIndex(testSteps.size() + 1);
        }
        // todo 更新人id、创建人id待做
        step.setCreateUserId("1");
        step.setUpdateUserId("1");

        int insert = testStepMapper.insert(step);
        return insert > 0;
    }

    @Override
    public List<TestStep> getStepBySceneId(Integer sceneId) {
        if (sceneId == null) {
            throw new RuntimeException("缺少场景id");
        }
        QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("scenario_id", sceneId);
        List<TestStep> testSteps = testStepMapper.selectList(queryWrapper);
        if (testSteps.isEmpty()) {
            return new ArrayList<>();
        }
        testSteps.sort(Comparator.comparing(TestStep::getOrderIndex));
        return testSteps;
    }

    @Override
    public Boolean updateStep(TestStep step) {
        if (step == null || step.getId() == null) {
            throw new RuntimeException("缺少必要参数");
        }
        // 更新请求可能不携带 scenarioId，从库中现有记录取
        TestStep dbStep = testStepMapper.selectById(step.getId());
        checkSceneDebugRunning(dbStep != null ? dbStep.getScenarioId() : step.getScenarioId());
        int update = testStepMapper.updateById(step);
        return update > 0;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteStep(Integer stepId) {
        if (stepId == null) {
            throw new RuntimeException("缺少步骤id");
        }

        // 先检查父节点是否存在
        TestStep parentStep = testStepMapper.selectById(stepId);
        if (parentStep == null) {
            throw new RuntimeException("步骤不存在，ID: " + stepId);
        }
        checkSceneDebugRunning(parentStep.getScenarioId());

        // 递归删除所有子节点及其后代节点
        deleteStepRecursively(parentStep);


        // 删除父节点
        int deleteParent = testStepMapper.deleteById(stepId);

        // 双重检查：必须确保父子节点都删除成功
        if (deleteParent <= 0) {
            throw new RuntimeException("删除父步骤失败，步骤ID: " + stepId);
        }

        // 重新排序
        Integer parentId = parentStep.getParentId();
        QueryWrapper<TestStep> queryWrapper2 = new QueryWrapper<>();
        queryWrapper2.eq("parent_id", parentId);
        queryWrapper2.eq("scenario_id", parentStep.getScenarioId());
        List<TestStep> testSteps = testStepMapper.selectList(queryWrapper2);
        if (!testSteps.isEmpty()) {
            testSteps.sort(Comparator.comparing(TestStep::getOrderIndex));
            for (int i = 0; i < testSteps.size(); i++) {
                testSteps.get(i).setOrderIndex(i + 1);
            }
            // 批量更新
            List<BatchResult> batchResults = testStepMapper.updateById(testSteps);
        }
        return true;
    }

    /**
     * 递归删除步骤及其所有子步骤
     *
     * @param parentStep 要删除的步骤
     */
    private void deleteStepRecursively(TestStep parentStep) {
        // 先删除所有子节点
        QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", parentStep.getId());
        List<TestStep> children = testStepMapper.selectList(queryWrapper);

        for (TestStep child : children) {
            // 递归删除子节点的所有后代
            deleteStepRecursively(child);
        }

        // 删除当前节点的所有直接子节点
        testStepMapper.delete(queryWrapper);
    }

    @Override
    @Transactional
    public Boolean updateStepSort(List<StepVO> testSteps) {
        if (testSteps == null || testSteps.isEmpty()) {
            return true;
        }
        checkSceneDebugRunning(testSteps.get(0).getScenarioId());

        // 将所有的数据TestStep递归打平
        List<TestStep> flatTestSteps = new ArrayList<>();
        for (StepVO stepVO : testSteps) {
            TestStep testStep = new TestStep();
            BeanUtils.copyProperties(stepVO, testStep);
            flatTestSteps.add(testStep);
            // 递归处理
            recursive(stepVO, flatTestSteps);
        }
        // 批量保存
        if (flatTestSteps.isEmpty()) return true;
        List<BatchResult> batchResults = testStepMapper.insertOrUpdate(flatTestSteps);

        boolean allSuccess = batchResults.stream().allMatch(result -> result.getUpdateCounts().length > 0);

        return allSuccess;
    }

    @Override
    public Boolean copyStep(Integer copyId) {
        if (copyId == null) {
            throw new RuntimeException("缺少步骤id");
        }
        TestStep testStep = testStepMapper.selectById(copyId);
        if (testStep == null) {
            throw new RuntimeException("复制步骤不存在，ID: " + copyId);
        }
        checkSceneDebugRunning(testStep.getScenarioId());
        Integer parentId = testStep.getParentId();
        testStep.setId(null);
        QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", parentId).eq("scenario_id", testStep.getScenarioId());
        List<TestStep> testSteps = testStepMapper.selectList(queryWrapper);
        testSteps.add(testStep);
        testSteps.sort(Comparator.comparing(TestStep::getOrderIndex));
        // 重新排序
        for (int i = 0; i < testSteps.size(); i++) {
            testSteps.get(i).setOrderIndex(i + 1);
        }
        // 批量保存
        List<BatchResult> batchResults = testStepMapper.insertOrUpdate(testSteps);
        boolean allSuccess = batchResults.stream().allMatch(result -> result.getUpdateCounts().length > 0);

        return allSuccess;
    }

    @Override
    public Boolean disableStep(Integer testStepId) {
        // 查询步骤是否存在
        TestStep testStep = testStepMapper.selectById(testStepId);
        if (testStep == null) {
            throw new RuntimeException("步骤不存在，ID: " + testStepId);
        }
        checkSceneDebugRunning(testStep.getScenarioId());
        List<TestStep> updateSteps = new ArrayList<>();
        // 递归处理状态（如果是禁用，就禁用本身极其所有子步骤；恢复也是一样）
        if (testStep.getIsDisable() == 1) {
            testStep.setIsDisable(0);
            updateSteps.add(testStep);
            recursiveUpdateStepDisable(testStep, updateSteps, 0);
        } else {
            testStep.setIsDisable(1);
            updateSteps.add(testStep);
            recursiveUpdateStepDisable(testStep, updateSteps, 1);
        }
        List<BatchResult> batchResults = testStepMapper.insertOrUpdate(updateSteps);
        return batchResults.stream().allMatch(result -> result.getUpdateCounts().length > 0);
    }

    @Override
    public Boolean addAdjacentStep(AddAdjacentStepDTO adjacentStep) {

        if (adjacentStep == null || "".equals(adjacentStep.getTargetStepId()) || adjacentStep.getAddStep() == null) {
            throw new ParamIsEmptyException("缺少步骤信息");
        }
        TestStep addStep = adjacentStep.getAddStep();
        String targetStepId = adjacentStep.getTargetStepId();
        Boolean isChildren = adjacentStep.getIsChildren();
        // 获取目标id
        TestStep targetStep = testStepMapper.selectById(targetStepId);
        if (targetStep == null) {
            throw new RuntimeException("目标步骤不存在，ID: " + targetStepId);
        }
        checkSceneDebugRunning(targetStep.getScenarioId());
        if (isChildren == null || !isChildren) { // 如果不是子步骤添加
            // 获取目标id的index
            Integer targetIndex = targetStep.getOrderIndex();
            // 在目标步骤后面插入
            addStep.setOrderIndex(targetIndex + 1);

            QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("parent_id", addStep.getParentId());
            queryWrapper.eq("scenario_id", addStep.getScenarioId());
            queryWrapper.eq("project_id", targetStep.getProjectId());
            // order_index 大于 targetIndex的
            queryWrapper.gt("order_index", targetIndex);
            List<TestStep> testSteps = testStepMapper.selectList(queryWrapper);
            // 在原有基础上，大于目标index的步骤全部index+1
            if (!testSteps.isEmpty()) {
                for (TestStep testStep : testSteps) {
                    testStep.setOrderIndex(testStep.getOrderIndex() + 1);
                }
                // 批量保存
                List<BatchResult> batchResults = testStepMapper.insertOrUpdate(testSteps);
            }
        } else { // 子步骤添加
            // 直接将 targetStepId 作为父id
            addStep.setParentId(targetStep.getId());
            // 获取改父节点下的步骤数量
            QueryWrapper<TestStep> testStepQueryWrapper = new QueryWrapper<>();
            testStepQueryWrapper.eq("parent_id", targetStep.getId());
            testStepQueryWrapper.eq("scenario_id", targetStep.getScenarioId());
            testStepQueryWrapper.eq("project_id", targetStep.getProjectId());
            Long childrenCount = testStepMapper.selectCount(testStepQueryWrapper);
            addStep.setOrderIndex(childrenCount.intValue() + 1);
        }

        // todo 更新人id、创建人id待做
        addStep.setCreateUserId("1");
        addStep.setUpdateUserId("1");
        int insert = testStepMapper.insert(addStep);
        return insert > 0;

    }

    @Override
    public Boolean batchEnableStep(List<Integer> stepIds) {
        if (stepIds == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        if (stepIds.isEmpty()) {
            return true;
        }
        // 批量查询
        List<TestStep> testSteps = testStepMapper.selectBatchIds(stepIds);
        if (testSteps.isEmpty()) {
            return true;
        }
        testSteps.stream().map(TestStep::getScenarioId).distinct().forEach(this::checkSceneDebugRunning);
        List<TestStep> updateStepList = new ArrayList<>();
        for (TestStep testStep : testSteps) {
            testStep.setIsDisable(0);
            updateStepList.add(testStep);
        }
        // 批量更新
        List<BatchResult> batchResults = testStepMapper.insertOrUpdate(updateStepList);
        return true;
    }

    @Override
    public Boolean batchDisableStep(List<Integer> stepIds) {
        if (stepIds == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        if (stepIds.isEmpty()) {
            return true;
        }
        // 批量查询
        List<TestStep> testSteps = testStepMapper.selectBatchIds(stepIds);
        if (testSteps.isEmpty()) {
            return true;
        }
        testSteps.stream().map(TestStep::getScenarioId).distinct().forEach(this::checkSceneDebugRunning);
        List<TestStep> updateStepList = new ArrayList<>();
        for (TestStep testStep : testSteps) {
            testStep.setIsDisable(1);
            updateStepList.add(testStep);
        }
        // 批量更新
        List<BatchResult> batchResults = testStepMapper.insertOrUpdate(updateStepList);
        return true;
    }

    @Override
    @Transactional
    public Boolean batchDeleteStep(List<Integer> stepIds) {
        if (stepIds == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        if (stepIds.isEmpty()) {
            return true;
        }
        // 批量查询
        List<TestStep> testSteps = testStepMapper.selectBatchIds(stepIds);
        if (testSteps.isEmpty()) {
            return true;
        }
        testSteps.stream().map(TestStep::getScenarioId).distinct().forEach(this::checkSceneDebugRunning);
        // 这里不用递归删除，是因为前端传递的参数，包含了子节点id

        // 批量删除
        testStepMapper.deleteByIds(testSteps);

        // 调整顺序
        // 1. 获取删除节点的父节点列表，去重
        Set<Integer> parentIds = testSteps.stream().map(TestStep::getParentId).collect(Collectors.toSet());
        List<Integer> parentIdsList = new ArrayList<>(parentIds);
        for (Integer parentId : parentIdsList) {
            QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("parent_id", parentId);
            queryWrapper.eq("scenario_id", testSteps.get(0).getScenarioId());
            queryWrapper.eq("project_id", testSteps.get(0).getProjectId());
            List<TestStep> childrenSteps = testStepMapper.selectList(queryWrapper);
            if (childrenSteps.isEmpty()) continue;
            childrenSteps.sort(Comparator.comparingInt(TestStep::getOrderIndex));
            // 重新排序
            for (int i = 0; i < childrenSteps.size(); i++) {
                childrenSteps.get(i).setOrderIndex(i + 1);
            }
            // 批量更新
            List<BatchResult> batchResults = testStepMapper.insertOrUpdate(childrenSteps);
        }
        return true;
    }


    private void recursiveUpdateStepDisable(TestStep parentStep, List<TestStep> updates, Integer disableStatus) {
        QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", parentStep.getId());
        queryWrapper.eq("scenario_id", parentStep.getScenarioId());
        List<TestStep> testSteps = testStepMapper.selectList(queryWrapper);
        if (testSteps.isEmpty()) return;
        for (TestStep testStep : testSteps) {
            testStep.setIsDisable(disableStatus);
            updates.add(testStep);
            recursiveUpdateStepDisable(testStep, updates, disableStatus);
        }
    }


    private void recursive(StepVO stepVO, List<TestStep> flatTestSteps) {
        // 获取子节点
        List<StepVO> childrenVos = stepVO.getChildren();
        if (childrenVos == null || childrenVos.isEmpty()) return;
        for (StepVO child : childrenVos) {
            TestStep testStep = new TestStep();
            BeanUtils.copyProperties(child, testStep);
            flatTestSteps.add(testStep);
            recursive(child, flatTestSteps);
        }
    }


    @Override
    @Transactional
    public Boolean importExistSceneStep(ImportExistSceneStepDTO existSceneStepDTO) {
        if (existSceneStepDTO == null) throw new ParamIsEmptyException("缺少参数");
        Integer targetSceneId = existSceneStepDTO.getTargetSceneId();
        if (targetSceneId == null) throw new ParamIsEmptyException("缺少目标场景ID");
        // 导入会往目标场景写入步骤，调试运行中拦截
        checkSceneDebugRunning(targetSceneId);

        // 优先按具体步骤ID导入（新逻辑）
        List<Integer> sourceStepIds = existSceneStepDTO.getSourceStepIds();
        if (sourceStepIds != null && !sourceStepIds.isEmpty()) {
            return importStepsByIds(targetSceneId, sourceStepIds);
        }

        // 兼容旧逻辑：按场景导入
        List<Integer> sourceSceneIds = existSceneStepDTO.getSourceSceneIds();
        if (sourceSceneIds != null && !sourceSceneIds.isEmpty()) {
            return importScenesByIds(targetSceneId, sourceSceneIds);
        }

        throw new ParamIsEmptyException("缺少源场景ID或源步骤ID");
    }

    /**
     * 按具体步骤ID导入：仅导入选中的步骤；若选中了子步骤，会自动补齐其直属父步骤以保持层级关系
     */
    private Boolean importStepsByIds(Integer targetSceneId, List<Integer> sourceStepIds) {
        Scene targetScene = sceneMapper.selectById(targetSceneId);
        if (targetScene == null) throw new ParamIsEmptyException("目标场景不存在");
        String targetProjectId = targetScene.getProjectId();

        List<TestStep> steps = testStepMapper.selectBatchIds(sourceStepIds);
        if (steps.isEmpty()) return true;

        // 安全校验：只能导入同一项目下的步骤
        for (TestStep step : steps) {
            if (step.getProjectId() != null && !step.getProjectId().equals(targetProjectId)) {
                throw new ParamIsEmptyException("只能导入同一项目下的步骤");
            }
        }

        // 收集需要导入的所有步骤：勾选的步骤 + 子步骤对应的直属父步骤
        Set<Integer> allImportIds = new HashSet<>(sourceStepIds);
        for (TestStep step : steps) {
            Integer parentId = step.getParentId();
            if (parentId != null && parentId != 0) {
                allImportIds.add(parentId);
            }
        }

        List<TestStep> allSteps = testStepMapper.selectBatchIds(new ArrayList<>(allImportIds));
        if (allSteps.isEmpty()) return true;

        // 获取目标场景当前步骤数量，用于给顶层步骤分配 orderIndex
        int currentSceneStepCount = testStepMapper.selectCount(
                new QueryWrapper<TestStep>().eq("scenario_id", targetSceneId)).intValue();

        // 构建旧ID -> 新实体的映射
        Map<Integer, TestStep> idMapping = new HashMap<>();
        List<TestStep> allNewEntities = new ArrayList<>();

        for (TestStep step : allSteps) {
            TestStep copy = new TestStep();
            BeanUtils.copyProperties(step, copy);
            copy.setId(null);
            copy.setCreatedAt(new Date());
            copy.setUpdatedAt(new Date());
            copy.setScenarioId(targetSceneId.toString());
            copy.setProjectId(targetProjectId);
            // 顶层节点（原parentId为0，或在选中列表中但父节点未被选中）重新分配orderIndex
            if (step.getParentId() == null || step.getParentId() == 0 || !allImportIds.contains(step.getParentId())) {
                copy.setParentId(0);
                copy.setOrderIndex(++currentSceneStepCount);
            } else {
                // 先保持原parentId，批量插入后再更新
                copy.setParentId(step.getParentId());
            }
            idMapping.put(step.getId(), copy);
            allNewEntities.add(copy);
        }

        // 批量保存
        testStepMapper.insert(allNewEntities);

        // 更新parentId为新ID
        List<TestStep> testStepsToUpdate = new ArrayList<>();
        for (TestStep step : allSteps) {
            TestStep newEntity = idMapping.get(step.getId());
            if (step.getParentId() != null && step.getParentId() != 0 && allImportIds.contains(step.getParentId())) {
                TestStep newParent = idMapping.get(step.getParentId());
                if (newParent != null) {
                    newEntity.setParentId(newParent.getId());
                    testStepsToUpdate.add(newEntity);
                }
            }
        }
        if (!testStepsToUpdate.isEmpty()) {
            testStepMapper.insertOrUpdate(testStepsToUpdate);
        }
        return true;
    }

    /**
     * 兼容旧逻辑：按场景导入所有步骤
     */
    private Boolean importScenesByIds(Integer targetSceneId, List<Integer> sourceSceneIds) {
        Scene targetScene = sceneMapper.selectById(targetSceneId);
        if (targetScene == null) throw new ParamIsEmptyException("目标场景不存在");
        String targetProjectId = targetScene.getProjectId();

        List<Scene> sourceScenes = sceneMapper.selectBatchIds(sourceSceneIds);
        if (sourceScenes.isEmpty()) return true;

        // 安全校验：只能导入同一项目下的场景
        for (Scene scene : sourceScenes) {
            if (scene.getProjectId() != null && !scene.getProjectId().equals(targetProjectId)) {
                throw new ParamIsEmptyException("只能导入同一项目下的场景");
            }
        }

        // 获取当前场景的步骤数量
        int currentSceneStepCount = testStepMapper.selectCount(new QueryWrapper<TestStep>().eq("scenario_id",
                targetSceneId)).intValue();
        // 2. 第一遍：创建所有新对象（无parentId）
        for (Scene scene : sourceScenes) {
            QueryWrapper<TestStep> queryWrapper
                    = new QueryWrapper<>();
            queryWrapper.eq("scenario_id", scene.getId());
            queryWrapper.eq("project_id", scene.getProjectId());
            // 获取scene下的所有步骤
            List<TestStep> steps = testStepMapper
                    .selectList(queryWrapper);
            if (steps.isEmpty()) continue;
            // 1. 构建映射关系（旧ID -> 新实体）
            Map<Integer, TestStep> idMapping = new HashMap<>();
            List<TestStep> allNewEntities = new ArrayList<>();

            for (TestStep step : steps) {
                TestStep copy = new TestStep();
                BeanUtils.copyProperties(step, copy);
                copy.setId(null);
                copy.setCreatedAt(new Date());
                copy.setUpdatedAt(new Date());
                copy.setScenarioId(targetSceneId.toString());
                copy.setProjectId(targetProjectId);
                // 判断是否为顶层节点
                if (step.getParentId() != null && step.getParentId() == 0) {
                    copy.setOrderIndex(++currentSceneStepCount);
                }
                idMapping.put(step.getId(), copy);
                allNewEntities.add(copy);
            }
            // 批量保存
            testStepMapper.insert(allNewEntities);


            // 3. 第二遍：设置新的parentId关系
            List<TestStep> testStepsToUpdate = new ArrayList<>();
            for (TestStep step : steps) {
                TestStep newEntity = idMapping.get(step.getId());
                if (step.getParentId() != null && step.getParentId() != 0) {
                    TestStep newParent = idMapping.get(step.getParentId());
                    if (newParent != null) {
                        newEntity.setParentId(newParent.getId());
                        testStepsToUpdate.add(newEntity);
                    }
                }
            }
            if (testStepsToUpdate.isEmpty()) continue;
            // 批量更新一次
            testStepMapper.insertOrUpdate(testStepsToUpdate);
        }
        return true;
    }


}




