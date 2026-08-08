package com.mokatest.platform.demos.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.uiEnum.scene.SceneType;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.domain.ui.SceneSetting;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.scene.SceneBatchExportDTO;
import com.mokatest.platform.demos.domain.ui.dto.scene.SceneBatchImportDTO;
import com.mokatest.platform.demos.domain.ui.dto.scene.SceneExportDTO;
import com.mokatest.platform.demos.domain.ui.dto.scene.SceneExportMeta;
import com.mokatest.platform.demos.domain.ui.dto.scene.StepExportDTO;
import com.mokatest.platform.demos.domain.ui.vo.SceneVO;
import com.mokatest.platform.demos.exception.ParamIsEmptyException;
import com.mokatest.platform.demos.listener.projectListener.ProjectUpdateEvent;
import com.mokatest.platform.demos.mapper.ElementMapper;
import com.mokatest.platform.demos.mapper.SceneMapper;
import com.mokatest.platform.demos.mapper.TestStepMapper;
import com.mokatest.platform.demos.service.SceneService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Resource;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.mokatest.platform.demos.domain.ui.uiEnum.scene.SceneType.FOLDER;
import static com.mokatest.platform.demos.domain.ui.uiEnum.scene.SceneType.SCENE;
import static com.mokatest.platform.demos.listener.projectListener.Enum.UpdateDataType.UI;

/**
 * @author: JingLong
 * @description 针对表【scene】的数据库操作Service实现
 * @createDate 2025-07-31 19:19:18
 */
@Service
public class SceneServiceImpl extends ServiceImpl<SceneMapper, Scene> implements SceneService {

    @Resource
    private SceneMapper sceneMapper;

    @Resource
    private TestStepMapper testStepMapper;

    @Resource
    private ElementMapper elementMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    private final Gson gson = new Gson();

    @Override
    public List<SceneVO> allSceneList(Integer projectId) {
        return allSceneList(projectId, null);
    }

    @Override
    public List<SceneVO> allSceneList(Integer projectId, String sceneCategory) {
        if (projectId == null) {
            throw new ParamIsEmptyException("缺少项目id");
        }
        // 根据场景id获取所有的场景列表
        QueryWrapper<Scene> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("project_id", projectId);
        if (sceneCategory != null && !sceneCategory.isEmpty()) {
            queryWrapper.eq("scene_category", sceneCategory);
        }
        List<Scene> scenes = sceneMapper.selectList(queryWrapper);
        // 创建根节点
        SceneVO root = new SceneVO();
        root.setId(0);
        root.setName("根目录");
        root.setSort(0);
        root.setSceneType(FOLDER);


        // 获取顶部的所有父节点
        List<Scene> parentList = scenes.stream().filter(scene -> {
            return 0 == scene.getParentId();
        }).toList();
        List<SceneVO> result = new ArrayList<>();
        for (Scene scene : parentList) {
            SceneVO sceneVO = new SceneVO();
            BeanUtils.copyProperties(scene, sceneVO);
            sceneVO.setChildren(new ArrayList<>());
            // 递归构建节点
            buildSceneVOTree(sceneVO, scenes);
            result.add(sceneVO);
        }
        // 根据sort排序
        result.sort(Comparator.comparingInt(SceneVO::getSort));

        root.setChildren(result);
        List<SceneVO> objects = new ArrayList<>();
        objects.add(root);
        return objects;
    }

    @Override
    @Transactional
    public Boolean addScene(Scene scene) {
        if (scene == null) {
            throw new ParamIsEmptyException("缺少场景信息");
        }
        // 获取父id
        Integer parentId = scene.getParentId();
        QueryWrapper<Scene> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", parentId);
        queryWrapper.eq("project_id", scene.getProjectId());
        if (scene.getSceneCategory() != null && !scene.getSceneCategory().isEmpty()) {
            queryWrapper.eq("scene_category", scene.getSceneCategory());
        }
        Long aLong = sceneMapper.selectCount(queryWrapper);
        scene.setSort(Integer.valueOf(aLong.toString()) + 1);

        // 目录未传入分类时兜底
        if (FOLDER.name().equals(String.valueOf(scene.getSceneType())) && (scene.getSceneCategory() == null || scene.getSceneCategory().isEmpty())) {
            scene.setSceneCategory("UI");
        }

        // 同步的创建场景的通用配置
        SceneSetting sceneConfig = new SceneSetting();
        // 转换为 JSON 字符串
        Gson gson = new Gson();
        String sceneSetting = gson.toJson(sceneConfig);
        scene.setSceneSetting(sceneSetting);
        if (sceneMapper.insert(scene) > 0) {
            // 发布项目更新事件
            // 判断是否是场景
            SceneType sceneType = SceneType.valueOf(scene.getSceneType().toString());
            if (SCENE.equals(sceneType)) {
                eventPublisher.publishEvent(new ProjectUpdateEvent(this, 1, scene.getProjectId(), UI));
            }
            return true;
        }
        return false;
    }

    @Override
    public Boolean updateScene(Scene scene) {
        if (scene == null) {
            throw new ParamIsEmptyException("缺少场景信息");
        }
        int update = sceneMapper.updateById(scene);
        return update > 0;
    }

    @Override
    public List<SceneVO> folderList(String projectId) {
        return folderList(projectId, null);
    }

    @Override
    public List<SceneVO> folderList(String projectId, String sceneCategory) {
        if (projectId == null || "".equals(projectId)) {
            throw new ParamIsEmptyException("缺少项目id");
        }
        // 根据场景id获取所有的场景列表
        QueryWrapper<Scene> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("project_id", projectId);
        queryWrapper.eq("scene_type", FOLDER);
        if (sceneCategory != null && !sceneCategory.isEmpty()) {
            queryWrapper.eq("scene_category", sceneCategory);
        }
        List<Scene> scenes = sceneMapper.selectList(queryWrapper);
        // 创建根节点
        SceneVO root = new SceneVO();
        root.setId(0);
        root.setName("根目录");
        root.setSort(0);
        root.setSceneType(FOLDER);

        // 获取顶部的所有父节点
        List<Scene> parentList = scenes.stream().filter(scene -> {
            return 0 == scene.getParentId();
        }).toList();
        List<SceneVO> result = new ArrayList<>();
        for (Scene scene : parentList) {
            SceneVO sceneVO = new SceneVO();
            BeanUtils.copyProperties(scene, sceneVO);
            sceneVO.setChildren(new ArrayList<>());
            // 递归构建节点
            buildSceneVOTree(sceneVO, scenes);
            result.add(sceneVO);
        }

        root.setChildren(result);
        List<SceneVO> objects = new ArrayList<>();
        objects.add(root);
        return objects;
    }

    @Override
    @Transactional
    public Boolean deleteFolderOrScene(Integer sceneId) {
        if (sceneId == null) {
            throw new ParamIsEmptyException("缺少场景id");
        }

        // 查询对应场景
        Scene scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            return true;
        }

        // 统计要删除的 SCENE 类型数量（包含自身）
        Integer sceneCount = sceneMapper.countSceneChildren(sceneId);

        // 1. 处理父目录排序
        sceneMapper.decrementSortAfter(scene.getParentId(), scene.getSort());


        // 2. 递归查询所有需要删除的场景ID（数据库层面递归）
        List<Integer> allSceneIds = sceneMapper.findAllChildrenIds(sceneId);
        allSceneIds.add(sceneId);  // 包含自身

        // 3. 查询这些场景下的所有测试步骤
        List<Integer> allTestStepIds = testStepMapper.findBySceneIds(allSceneIds);

        // 4. 物理删除测试步骤，逻辑删除场景
        if (!allTestStepIds.isEmpty()) {
            testStepMapper.deleteBatchIds(allTestStepIds);
        }
        if (!allSceneIds.isEmpty()) {
            List<Scene> scenes = sceneMapper.selectBatchIds(allSceneIds);
            Date now = new Date();
            for (Scene sceneItem : scenes) {
                if (sceneItem != null) {
                    sceneItem.setDeletedAt(now);
                    sceneMapper.deleteById(sceneItem);
                }
            }
        }


        // 事件发布，触发项目表的数据更新
        eventPublisher.publishEvent(new ProjectUpdateEvent(this, -sceneCount, scene.getProjectId(), UI));

        return true;
    }


    @Override
    public Boolean debugScene(Integer sceneId) {
        if (sceneId == null) {
            throw new ParamIsEmptyException("缺少场景id");
        }

        return null;
    }

    @Override
    @Transactional
    public Boolean importScene(Map<String, Object> sceneData) {
        if (sceneData == null) {
            throw new ParamIsEmptyException("缺少场景信息");
        }
        List<TestStep> stepList = convertWithGson((List<LinkedHashMap<String, Object>>) sceneData.get("stepList"));
        Map sceneMap = (Map) sceneData.get("scene");
        Scene scene = new Scene();
        scene.setSceneType(SCENE);
        scene.setSceneCategory("UI");
        scene.setName(sceneMap.get("name").toString());
        scene.setDescription(sceneMap.get("description").toString());
        scene.setParentId(Integer.valueOf(sceneMap.get("parentId").toString()));
        scene.setProjectId(sceneMap.get("projectId").toString());

        // 根据父id获取排序
        QueryWrapper<Scene> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", scene.getParentId());
        List<Scene> scenes = sceneMapper.selectList(queryWrapper);
        if (scenes.isEmpty()) {
            scene.setSort(1);
        } else {
            scene.setSort(scenes.size() + 1);
        }

        scene.setCreateUserId(StpUtil.getLoginIdAsString());
        scene.setUpdateUserId(StpUtil.getLoginIdAsString());
        // 创建配置
        SceneSetting sceneSetting = new SceneSetting();
        // 转JSON字符串
        scene.setSceneSetting(new Gson().toJson(sceneSetting));

        // 添加场景
        int insert = sceneMapper.insert(scene);
        if (insert < 1) {
            throw new RuntimeException("添加场景失败");
        }


        Integer sceneId = scene.getId();
        if (stepList == null || stepList.isEmpty()) return true;
        for (TestStep testStep : stepList) {
            // todo 创建者id
            testStep.setCreateUserId("11");
            testStep.setUpdateUserId("11");
            testStep.setScenarioId(sceneId.toString());
        }
        // 批量添加步骤
        testStepMapper.insertOrUpdate(stepList);
        return true;
    }

    @Override
    public List<Scene> getSceneListByIds(List<Integer> sceneIdList) {
        if (sceneIdList == null || sceneIdList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Scene> scenes = sceneMapper.selectBatchIds(sceneIdList);

        // 使用LinkedHashMap保持插入顺序
        Map<Integer, Scene> sceneMap = new LinkedHashMap<>();
        for (Scene scene : scenes) {
            sceneMap.put(scene.getId(), scene);
        }

        // 按照原始顺序返回
        List<Scene> result = new ArrayList<>();
        for (Integer id : sceneIdList) {
            if (sceneMap.containsKey(id)) {
                result.add(sceneMap.get(id));
            }
        }

        return result;
    }

    @Override
    @Transactional
    public Boolean copyScene(Integer sourceSceneId) {
        if (sourceSceneId == null) throw new ParamIsEmptyException("缺少场景id");
        Scene scene = sceneMapper.selectById(sourceSceneId);
        if (scene == null) throw new RuntimeException("场景不存在");
        // 清空id
        scene.setId(null);
        scene.setName(scene.getName() + "副本");
        // 复制到当前父节点下的最后一个
        QueryWrapper<Scene> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", scene.getParentId());
        queryWrapper.eq("project_id", scene.getProjectId());
        scene.setSort(sceneMapper.selectCount(queryWrapper).intValue() + 1);
        scene.setCreateUserId(StpUtil.getLoginIdAsString());
        int insert = sceneMapper.insert(scene);
        if (insert <= 0) {
            throw new RuntimeException("场景复制失败");
        }

        List<TestStep> copyStepList = testStepMapper.selectList(new QueryWrapper<TestStep>().eq("scenario_id",
                sourceSceneId));
        if (copyStepList != null && !copyStepList.isEmpty()) {
            // 创建映射关系，记录原ID到新ID的对应关系
            Map<Integer, Integer> idMapping = new HashMap<>();

            // 按照orderIndex排序，确保父节点先被处理
            copyStepList.sort(Comparator.comparing(TestStep::getOrderIndex));

            // 第一步：复制所有步骤，获取新的ID
            for (TestStep testStep : copyStepList) {
                Integer oldId = testStep.getId();
                testStep.setId(null);
                testStep.setScenarioId(scene.getId().toString());
                // 插入数据库获取新ID
                int insert1 = testStepMapper.insert(testStep);
                if (insert1 <= 0) {
                    throw new RuntimeException("场景复制失败");
                }
                // 记录ID映射关系
                idMapping.put(oldId, testStep.getId());
            }

            // 第二步：更新parentId以维持嵌套关系
            for (TestStep testStep : copyStepList) {
                Integer oldParentId = testStep.getParentId();
                if (oldParentId != null && idMapping.containsKey(oldParentId)) {
                    // 根据映射关系更新parentId
                    Integer newParentId = idMapping.get(oldParentId);
                    testStep.setParentId(newParentId);
                    testStepMapper.updateById(testStep);
                }
            }
        }

        // 触发项目更新事件
        eventPublisher.publishEvent(new ProjectUpdateEvent(this, 1, scene.getProjectId(), UI));

        return true;
    }

    @Override
    public Boolean updateSceneSort(List<SceneVO> sceneList) {
        if (sceneList == null || sceneList.isEmpty()) return true;
        // 将数据打平
        List<Scene> flatScenes = new ArrayList<>();
        for (SceneVO sceneVO : sceneList) {
            Scene scene = new Scene();
            BeanUtils.copyProperties(sceneVO, scene);
            flatScenes.add(scene);
            flatSceneTree(sceneVO, flatScenes);
        }
        // 批量保存
        if (flatScenes.isEmpty()) return true;
        List<BatchResult> batchResults = sceneMapper.updateById(flatScenes);
        boolean allSuccess = batchResults.stream().allMatch(result -> result.getUpdateCounts().length > 0);
        return allSuccess;
    }

    @Override
    public Boolean updateSceneSetting(Map<String, Object> sceneSetting) {
        if (sceneSetting == null || sceneSetting.isEmpty()) return true;
        String sceneId = sceneSetting.get("sceneId").toString();
        if (sceneId == null || "".equals(sceneId)) throw new ParamIsEmptyException("缺少场景id");
        Scene scene = sceneMapper.selectById(sceneId);
        if (scene == null) throw new RuntimeException("场景不存在");
        String sceneSettingStr = sceneSetting.get("sceneSetting").toString();
        if (sceneSettingStr == null || "".equals(sceneSettingStr)) return true;
        scene.setSceneSetting(sceneSettingStr);
        return sceneMapper.updateById(scene) > 0;
    }

    private void flatSceneTree(SceneVO parentVO, List<Scene> result) {
        if (parentVO.getChildren() != null && !parentVO.getChildren().isEmpty()) {
            // 递归添加
            for (SceneVO childVO : parentVO.getChildren()) {
                Scene scene = new Scene();
                BeanUtils.copyProperties(childVO, scene);
                result.add(scene);
                flatSceneTree(childVO, result);
            }
        }

    }


    private void buildSceneVOTree(SceneVO parentSceneVO, List<Scene> scenes) {
        // 如果场景类型是scene就停止递归
        if (SCENE.equals(parentSceneVO.getSceneType())) {
            return;
        }
        // 寻找子节点
        Integer parentId = parentSceneVO.getId();
        List<Scene> childNode = scenes.stream().filter(scene1 -> {
            return parentId.equals(scene1.getParentId());
        }).toList();
        if (childNode.isEmpty()) return;
        for (Scene childScene : childNode) {
            SceneVO childSceneVO = new SceneVO();
            BeanUtils.copyProperties(childScene, childSceneVO);
            childSceneVO.setChildren(new ArrayList<>());
            buildSceneVOTree(childSceneVO, scenes);
            parentSceneVO.getChildren().add(childSceneVO);
        }
        parentSceneVO.getChildren().sort(Comparator.comparingInt(SceneVO::getSort));

    }


    @Override
    public SceneExportDTO exportScene(Integer sceneId) {
        if (sceneId == null) {
            throw new ParamIsEmptyException("缺少场景ID");
        }
        Scene scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new RuntimeException("场景不存在");
        }

        List<TestStep> steps = testStepMapper.selectList(
                new QueryWrapper<TestStep>()
                        .eq("scenario_id", sceneId)
                        .orderByAsc("order_index")
        );

        SceneExportDTO dto = new SceneExportDTO();
        dto.setExportedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        SceneExportMeta meta = new SceneExportMeta();
        meta.setName(scene.getName());
        meta.setDescription(scene.getDescription());
        meta.setSceneCategory(scene.getSceneCategory());
        meta.setSceneSetting(stripApiEnvConfig(scene.getSceneSetting()));
        dto.setMeta(meta);

        List<StepExportDTO> stepExportList = new ArrayList<>();
        for (TestStep step : steps) {
            StepExportDTO stepExport = new StepExportDTO();
            BeanUtils.copyProperties(step, stepExport);
            stepExport.setStepDetail(sanitizeStepDetailForExport(step.getStepDetail(), step.getStepType()));
            stepExportList.add(stepExport);
        }
        dto.setSteps(stepExportList);
        return dto;
    }

    @Override
    public SceneBatchExportDTO exportScenes(List<Integer> sceneIds) {
        if (sceneIds == null || sceneIds.isEmpty()) {
            throw new ParamIsEmptyException("缺少场景ID");
        }
        SceneBatchExportDTO batch = new SceneBatchExportDTO();
        batch.setExportedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        List<SceneExportDTO> scenes = new ArrayList<>();
        for (Integer sceneId : sceneIds) {
            scenes.add(exportScene(sceneId));
        }
        batch.setScenes(scenes);
        return batch;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean importScenesJson(SceneBatchImportDTO dto) {
        if (dto == null || dto.getSceneDataList() == null || dto.getSceneDataList().isEmpty()) {
            throw new ParamIsEmptyException("缺少场景数据");
        }
        if (dto.getProjectId() == null || dto.getProjectId().isEmpty()) {
            throw new ParamIsEmptyException("缺少项目ID");
        }

        Integer parentId = dto.getParentId();
        if (parentId == null) {
            parentId = 0;
        }

        QueryWrapper<Scene> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", parentId);
        queryWrapper.eq("project_id", dto.getProjectId());
        int baseSort = sceneMapper.selectCount(queryWrapper).intValue();

        int index = 1;
        for (SceneExportDTO sceneData : dto.getSceneDataList()) {
            importSingleScene(sceneData, parentId, dto.getProjectId(), baseSort + index++, dto.getSceneCategory());
        }

        eventPublisher.publishEvent(new ProjectUpdateEvent(this, 1, dto.getProjectId(), UI));
        return true;
    }

    /**
     * 导入单个场景（批量导入内部复用）
     */
    private void importSingleScene(SceneExportDTO exportData, Integer parentId, String projectId, int sort, String targetCategory) {
        SceneExportMeta meta = exportData.getMeta();
        if (meta == null) {
            throw new ParamIsEmptyException("缺少场景元数据");
        }

        // 分类优先级：当前页面传入 > 导出文件 meta > 默认 UI
        String sceneCategory = targetCategory;
        if (sceneCategory == null || sceneCategory.isEmpty()) {
            sceneCategory = meta.getSceneCategory();
        }
        if (!"API".equals(sceneCategory)) {
            sceneCategory = "UI";
        }

        Scene scene = new Scene();
        scene.setName(meta.getName());
        scene.setDescription(meta.getDescription());
        scene.setSceneCategory(sceneCategory);
        scene.setSceneType(SCENE);
        scene.setParentId(parentId);
        scene.setProjectId(projectId);
        scene.setSort(sort);
        scene.setCreateUserId(StpUtil.getLoginIdAsString());
        scene.setUpdateUserId(StpUtil.getLoginIdAsString());
        scene.setSceneSetting(meta.getSceneSetting());
        sceneMapper.insert(scene);

        Integer newSceneId = scene.getId();
        List<StepExportDTO> steps = exportData.getSteps();
        if (steps == null || steps.isEmpty()) {
            return;
        }

        List<TestStep> newSteps = new ArrayList<>();
        Map<Integer, Integer> idMapping = new HashMap<>();
        for (StepExportDTO stepExport : steps) {
            TestStep testStep = new TestStep();
            BeanUtils.copyProperties(stepExport, testStep);
            Integer oldId = stepExport.getId();
            testStep.setId(null);
            testStep.setProjectId(projectId);
            testStep.setScenarioId(newSceneId.toString());
            testStep.setCreateUserId(StpUtil.getLoginIdAsString());
            testStep.setUpdateUserId(StpUtil.getLoginIdAsString());
            testStep.setCreatedAt(new Date());
            testStep.setUpdatedAt(new Date());
            testStep.setStepDetail(sanitizeStepDetailForImport(stepExport.getStepDetail()));
            testStepMapper.insert(testStep);
            if (oldId != null) {
                idMapping.put(oldId, testStep.getId());
            }
            newSteps.add(testStep);
        }

        for (TestStep testStep : newSteps) {
            Integer oldParentId = testStep.getParentId();
            if (oldParentId != null && oldParentId != 0) {
                Integer newParentId = idMapping.get(oldParentId);
                testStep.setParentId(newParentId != null ? newParentId : 0);
                testStepMapper.updateById(testStep);
            }
        }
    }

    /**
     * 导出时清洗场景配置：去掉 API 环境相关字段
     */
    private String stripApiEnvConfig(String sceneSettingStr) {
        if (sceneSettingStr == null || sceneSettingStr.isEmpty()) {
            return sceneSettingStr;
        }
        try {
            SceneSetting setting = gson.fromJson(sceneSettingStr, SceneSetting.class);
            if (setting == null) {
                return sceneSettingStr;
            }
            setting.setApiEnvConfig(null);
            if (setting.getApiSceneConfig() != null) {
                setting.getApiSceneConfig().setEnvironmentId(null);
                setting.getApiSceneConfig().setEnvironmentName(null);
            }
            return gson.toJson(setting);
        } catch (Exception e) {
            return sceneSettingStr;
        }
    }

    /**
     * 导出时清洗步骤详情：元素库引用转自定义、API 环境剥离、fileIds 清空
     */
    private Object sanitizeStepDetailForExport(Object stepDetail, String stepType) {
        if (stepDetail == null) {
            return null;
        }
        Object json = toJsonObject(stepDetail);
        if (!(json instanceof Map)) {
            return stepDetail;
        }
        Map<String, Object> detail = gson.fromJson(gson.toJson(json), new TypeToken<Map<String, Object>>() {
        }.getType());
        if (detail == null) {
            return stepDetail;
        }

        for (String key : Arrays.asList("element", "dragElement", "targetElement")) {
            Object element = detail.get(key);
            if (element instanceof Map) {
                sanitizeElementForExport((Map<String, Object>) element);
            }
        }

        // 剥离录制插件的冗余字段
        detail.remove("locatorCandidates");
        detail.remove("recorded");

        if ("API_REQUEST".equals(stepType) || "SQL".equals(stepType)) {
            detail.put("apiRequestId", null);
            Object apiConfig = detail.get("apiConfig");
            if (apiConfig instanceof Map) {
                Map<String, Object> configMap = (Map<String, Object>) apiConfig;
                configMap.remove("id");
                configMap.remove("parentId");
                configMap.remove("projectId");
                configMap.remove("teamId");
                configMap.remove("sourceDratId");
                configMap.remove("envInfo");
            }
        }

        if ("FILE_UPLOAD".equals(stepType)) {
            detail.put("fileIds", new ArrayList<>());
        }

        return detail;
    }

    /**
     * 导入时二次兜底清洗步骤详情
     */
    private Object sanitizeStepDetailForImport(Object stepDetail) {
        if (stepDetail == null) {
            return null;
        }
        Object json = toJsonObject(stepDetail);
        if (!(json instanceof Map)) {
            return stepDetail;
        }
        Map<String, Object> detail = gson.fromJson(gson.toJson(json), new TypeToken<Map<String, Object>>() {
        }.getType());
        if (detail == null) {
            return stepDetail;
        }

        for (String key : Arrays.asList("element", "dragElement", "targetElement")) {
            Object element = detail.get(key);
            if (element instanceof Map) {
                sanitizeElementForImport((Map<String, Object>) element);
            }
        }

        // 剥离录制插件的冗余字段
        detail.remove("locatorCandidates");
        detail.remove("recorded");

        Object apiConfig = detail.get("apiConfig");
        if (apiConfig instanceof Map) {
            Map<String, Object> configMap = (Map<String, Object>) apiConfig;
            configMap.remove("id");
            configMap.remove("parentId");
            configMap.remove("projectId");
            configMap.remove("teamId");
            configMap.remove("sourceDratId");
            configMap.remove("envInfo");
        }

        if (detail.containsKey("fileIds")) {
            detail.put("fileIds", new ArrayList<>());
        }

        return detail;
    }

    /**
     * 导出时处理元素引用：元素库定位转自定义，locator 置空对象
     */
    private void sanitizeElementForExport(Map<String, Object> elementMap) {
        Object locator = elementMap.get("locator");
        if (locator instanceof Map) {
            Map<String, Object> locatorMap = (Map<String, Object>) locator;
            Object idObj = locatorMap.get("id");
            Integer elementId = parseInteger(idObj);
            if (elementId != null) {
                Element el = elementMapper.selectById(elementId);
                if (el != null) {
                    Map<String, Object> customLocator = new LinkedHashMap<>();
                    customLocator.put("locatorType", el.getLocatorType());
                    customLocator.put("locatorValue", el.getLocatorValue());
                    elementMap.put("customLocator", customLocator);
                }
            }
        }
        // 前端 ElementSelect 期望 locator 是对象而不是 null，置为空对象
        elementMap.put("locator", new LinkedHashMap<>());
        if (!elementMap.containsKey("customLocator") || elementMap.get("customLocator") == null) {
            elementMap.put("customLocator", new LinkedHashMap<>());
        }
    }

    /**
     * 导入时兜底处理元素引用：确保 locator/customLocator 是对象
     */
    private void sanitizeElementForImport(Map<String, Object> elementMap) {
        elementMap.put("locator", new LinkedHashMap<>());
        if (!elementMap.containsKey("customLocator") || elementMap.get("customLocator") == null) {
            elementMap.put("customLocator", new LinkedHashMap<>());
        }
    }

    /**
     * 兼容 stepDetail 在 DB 中以 JSON 字符串存储的情况
     */
    private Object toJsonObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String str = (String) value;
            if (str.trim().isEmpty()) {
                return null;
            }
            return gson.fromJson(str, Object.class);
        }
        return value;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<TestStep> convertWithGson(List<LinkedHashMap<String, Object>> mapList) {
        Gson gson = new Gson();

        // 先将LinkedHashMap列表转换为JSON字符串
        String json = gson.toJson(mapList);

        // 然后从JSON字符串转换回实体对象列表
        Type type = new TypeToken<List<TestStep>>() {
        }.getType();
        return gson.fromJson(json, type);
    }
}




