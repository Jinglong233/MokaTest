package com.mokatest.platform.demos.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.exception.ParamIsEmptyException;
import com.mokatest.platform.demos.mapper.SceneMapper;
import com.mokatest.platform.demos.mapper.TestStepMapper;
import com.mokatest.platform.demos.service.RecordService;
import com.google.gson.Gson;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 录制导入服务实现
 */
@Service
public class RecordServiceImpl implements RecordService {

    @Resource
    private RecordEventConverter recordEventConverter;

    @Resource
    private SceneMapper sceneMapper;

    @Resource
    private TestStepMapper testStepMapper;

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
        if (!StringUtils.hasText(dto.getName())) {
            throw new ParamIsEmptyException("缺少场景名称");
        }
        if (!StringUtils.hasText(dto.getProjectId())) {
            throw new ParamIsEmptyException("缺少项目 ID");
        }
        if (dto.getParentId() == null) {
            dto.setParentId(0);
        }
        if (CollectionUtils.isEmpty(dto.getSteps())) {
            throw new BusinessException("步骤列表不能为空");
        }

        // 1. 创建场景
        Scene scene = new Scene();
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

        String userId = StpUtil.getLoginIdAsString();
        scene.setCreateUserId(userId);
        scene.setUpdateUserId(userId);

        sceneMapper.insert(scene);

        // 2. 批量插入步骤
        int order = 1;
        for (RecordStepDraftVO draft : dto.getSteps()) {
            validateDraft(draft);
            TestStep step = new TestStep();
            step.setScenarioId(scene.getId().toString());
            step.setStepType(draft.getStepType());
            step.setStepName(draft.getStepName());
            step.setOrderIndex(order++);
            step.setParentId(0);
            step.setProjectId(dto.getProjectId());
            step.setIsDisable(0);
            step.setStepDetail(draft.getStepDetail());
            step.setCreateUserId(userId);
            step.setUpdateUserId(userId);
            testStepMapper.insert(step);
        }

        return scene.getId();
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
