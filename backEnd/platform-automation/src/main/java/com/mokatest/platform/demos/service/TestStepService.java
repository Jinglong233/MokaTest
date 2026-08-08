package com.mokatest.platform.demos.service;

import com.mokatest.platform.demos.domain.ui.TestStep;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.domain.ui.dto.other.AddAdjacentStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.other.ImportExistSceneStepDTO;
import com.mokatest.platform.demos.domain.ui.vo.StepVO;

import java.util.List;

/**
 * @author: JingLong
 * @description 针对表【test_step(测试步骤主表)】的数据库操作Service
 * @createDate 2025-07-26 11:10:33
 */
public interface TestStepService extends IService<TestStep> {

    List<StepVO> getStepList(Integer sceneId);

    TestStep getStepDetail(Integer stepId);

    Boolean addStep(TestStep step);

    List<TestStep> getStepBySceneId(Integer sceneId);

    Boolean updateStep(TestStep step);

    Boolean deleteStep(Integer step);

    Boolean updateStepSort(List<StepVO> testStepList);

    Boolean copyStep(Integer copyId);

    Boolean disableStep(Integer testStepId);

    Boolean addAdjacentStep(AddAdjacentStepDTO adjacentStep);

    Boolean batchEnableStep(List<Integer> stepIds);

    Boolean batchDisableStep(List<Integer> stepIds);

    Boolean batchDeleteStep(List<Integer> stepIds);

    Boolean importExistSceneStep(ImportExistSceneStepDTO existSceneStepDTO);
}
