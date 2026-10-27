package com.mokatest.platform.demos.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.domain.ui.ImportBatchRecord;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.domain.ui.SceneSetting;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordImportResultVO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordSaveDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordStepDraftVO;
import com.mokatest.platform.demos.domain.ui.record.RecordConvertResult;
import com.mokatest.platform.demos.domain.ui.record.RecordEventConverter;
import com.mokatest.platform.demos.domain.ui.record.RecordFileValidator;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.element.ElementLibrarySyncService;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.exception.ParamIsEmptyException;
import com.mokatest.platform.demos.listener.projectListener.ProjectUpdateEvent;
import com.mokatest.platform.demos.mapper.ImportBatchRecordMapper;
import com.mokatest.platform.demos.mapper.SceneMapper;
import com.mokatest.platform.demos.mapper.TestStepMapper;
import com.mokatest.platform.demos.service.RecordService;
import com.google.gson.Gson;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static com.mokatest.platform.demos.listener.projectListener.Enum.UpdateDataType.UI;

/**
 * 录制导入服务实现
 */
@Service
@Slf4j
public class RecordServiceImpl implements RecordService {

    @Resource
    private RecordEventConverter recordEventConverter;

    @Resource
    private SceneMapper sceneMapper;

    @Resource
    private TestStepMapper testStepMapper;

    @Resource
    private ImportBatchRecordMapper importBatchRecordMapper;

    @Resource
    private ElementLibrarySyncService elementLibrarySyncService;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Gson gson = new Gson();

    @Override
    public RecordImportResultVO importRecord(MultipartFile file, Integer projectId) {
        RecordFileValidator.validateFile(file);
        String content = RecordFileValidator.readFileContent(file);

        com.mokatest.platform.demos.domain.ui.dto.record.RecordFileDTO fileDTO;
        try {
            fileDTO = objectMapper.readValue(content,
                    com.mokatest.platform.demos.domain.ui.dto.record.RecordFileDTO.class);
        } catch (Exception e) {
            throw new BusinessException("录制文件 JSON 解析失败：" + e.getMessage());
        }

        RecordFileValidator.validateContent(fileDTO);
        RecordConvertResult result = recordEventConverter.convert(fileDTO, projectId);

        RecordImportResultVO vo = new RecordImportResultVO();
        vo.setSteps(result.getSteps());
        vo.setWarnings(result.getWarnings());
        vo.setSkipped(result.getSkipped());
        return vo;
    }

    @Override
    @Transactional
    public Integer save(RecordSaveDTO dto) {
        if (dto == null) {
            throw new ParamIsEmptyException("缺少保存信息");
        }
        if (!StringUtils.hasText(dto.getProjectId())) {
            throw new ParamIsEmptyException("缺少项目 ID");
        }
        if (CollectionUtils.isEmpty(dto.getSteps())) {
            throw new BusinessException("步骤列表不能为空");
        }

        // 幂等：重复提交直接返回首次保存的场景 ID
        if (StringUtils.hasText(dto.getBatchId())) {
            String existedResult = findBatchResult(dto.getBatchId());
            if (existedResult != null) {
                return Integer.valueOf(existedResult);
            }
        }

        String userId = StpUtil.getLoginIdAsString();
        Scene scene;
        int order;
        boolean append = dto.getSceneId() != null;
        if (append) {
            // 继续录制：校验场景归属当前项目，步骤追加到末尾
            scene = sceneMapper.selectById(dto.getSceneId());
            if (scene == null) {
                throw new BusinessException("目标场景不存在");
            }
            if (!dto.getProjectId().equals(scene.getProjectId())) {
                throw new BusinessException("目标场景不属于当前项目");
            }
            order = resolveNextOrderIndex(scene.getId());
        } else {
            if (!StringUtils.hasText(dto.getName())) {
                throw new ParamIsEmptyException("缺少场景名称");
            }
            if (dto.getParentId() == null) {
                dto.setParentId(0);
            }
            // 1. 创建场景
            scene = new Scene();
            scene.setProjectId(dto.getProjectId());
            scene.setName(dto.getName().trim());
            scene.setParentId(dto.getParentId());
            if (dto.getDescription() != null) {
                scene.setDescription(dto.getDescription().trim());
            }
            scene.setSceneType("SCENE");
            scene.setSceneCategory("UI");

            QueryWrapper<Scene> countWrapper = new QueryWrapper<>();
            countWrapper.eq("parent_id", dto.getParentId());
            countWrapper.eq("project_id", dto.getProjectId());
            countWrapper.eq("scene_category", "UI");
            scene.setSort(sceneMapper.selectCount(countWrapper).intValue() + 1);

            SceneSetting sceneSetting = new SceneSetting();
            scene.setSceneSetting(gson.toJson(sceneSetting));

            scene.setCreateUserId(userId);
            scene.setUpdateUserId(userId);

            sceneMapper.insert(scene);
            order = 1;
        }

        // 同步元素至元素库：解析目标目录（缺省 = 与场景所属目录同名的元素库目录）
        Integer elementFolderId = null;
        if (Boolean.TRUE.equals(dto.getSyncElementsToLibrary())) {
            elementFolderId = elementLibrarySyncService.resolveTargetFolderId(
                    dto.getProjectId(), dto.getElementParentId(), scene.getParentId());
        }

        // 2. 批量插入步骤（追加模式 orderIndex 从现有最大值续排）
        for (RecordStepDraftVO draft : dto.getSteps()) {
            validateDraft(draft);
            Object stepDetail = draft.getStepDetail();
            if (elementFolderId != null) {
                stepDetail = syncStepDetail(stepDetail, dto.getProjectId(), elementFolderId);
            }
            TestStep step = new TestStep();
            step.setScenarioId(scene.getId().toString());
            step.setStepType(draft.getStepType());
            step.setStepName(draft.getStepName());
            step.setOrderIndex(order++);
            step.setParentId(0);
            step.setProjectId(dto.getProjectId());
            step.setIsDisable(0);
            step.setStepDetail(stepDetail);
            step.setCreateUserId(userId);
            step.setUpdateUserId(userId);
            testStepMapper.insert(step);
        }

        if (!append) {
            // 仅新建场景时发布项目更新事件（追加不改变场景数量）
            eventPublisher.publishEvent(new ProjectUpdateEvent(this, 1, scene.getProjectId(), UI));
        }
        if (StringUtils.hasText(dto.getBatchId())) {
            saveBatchRecord(dto.getBatchId(), dto.getProjectId(), "RECORD_SAVE", scene.getId().toString());
        }
        return scene.getId();
    }

    /**
     * 追加模式：取场景当前最大 orderIndex + 1
     */
    private int resolveNextOrderIndex(Integer sceneId) {
        QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("scenario_id", sceneId);
        queryWrapper.orderByDesc("order_index");
        queryWrapper.last("LIMIT 1");
        TestStep last = testStepMapper.selectOne(queryWrapper);
        return last != null && last.getOrderIndex() != null ? last.getOrderIndex() + 1 : 1;
    }

    /**
     * 步骤详情中的元素同步到元素库并重映射引用；stepDetail 可能是 Map 或 JSON 字符串
     */
    @SuppressWarnings("unchecked")
    private Object syncStepDetail(Object stepDetail, String projectId, Integer elementFolderId) {
        if (stepDetail instanceof Map) {
            elementLibrarySyncService.syncStepDetailElements((Map<String, Object>) stepDetail, projectId,
                    elementFolderId);
            return stepDetail;
        }
        if (stepDetail instanceof String && StringUtils.hasText((String) stepDetail)) {
            Map<String, Object> detailMap = gson.fromJson((String) stepDetail, Map.class);
            elementLibrarySyncService.syncStepDetailElements(detailMap, projectId, elementFolderId);
            return detailMap;
        }
        return stepDetail;
    }

    /**
     * 按幂等批次ID查询首次保存结果，未保存过返回 null；
     * entry_type 限定本入口，避免同 batchId 撞到他入口类型后结果解析失败
     */
    private String findBatchResult(String batchId) {
        QueryWrapper<ImportBatchRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("batch_id", batchId);
        queryWrapper.eq("entry_type", "RECORD_SAVE");
        ImportBatchRecord record = importBatchRecordMapper.selectOne(queryWrapper);
        return record == null ? null : record.getResult();
    }

    /**
     * 写入幂等批次记录；并发下撞唯一键说明另一请求已完成保存，按首次结果处理
     */
    private void saveBatchRecord(String batchId, String projectId, String entryType, String result) {
        ImportBatchRecord record = new ImportBatchRecord();
        record.setBatchId(batchId);
        record.setProjectId(projectId);
        record.setEntryType(entryType);
        record.setResult(result);
        try {
            importBatchRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.warn("[Record] batchId={} 已被并发请求写入，按首次保存结果处理", batchId);
        }
    }

    private void validateDraft(RecordStepDraftVO draft) {
        if (draft == null) {
            throw new BusinessException("步骤草稿不能为空");
        }
        if (!StringUtils.hasText(draft.getStepType())) {
            throw new BusinessException("步骤类型不能为空");
        }
        try {
            StepType.valueOf(draft.getStepType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("非法的步骤类型：" + draft.getStepType());
        }
        if (draft.getStepDetail() == null) {
            throw new BusinessException("步骤详情不能为空");
        }
    }

}
