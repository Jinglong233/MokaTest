package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.other.AddAdjacentStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.other.ImportExistSceneStepDTO;
import com.mokatest.platform.demos.domain.ui.vo.ResponseVO;
import com.mokatest.platform.demos.domain.ui.vo.StepVO;
import com.mokatest.platform.demos.service.TestStepService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UI 自动化测试步骤管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   查看步骤：auto:step:view
 *   添加步骤：auto:step:create
 *   编辑步骤：auto:step:update
 *   删除步骤：auto:step:delete
 * admin 角色默认拥有上述所有权限。
 */
@RestController
@RequestMapping("/step")
public class StepController {

    @Resource
    private TestStepService testStepService;

    /**
     * 获取场景下的步骤列表
     * 权限：auto:step:view
     */
    @SaCheckPermission("auto:step:view")
    @RequestMapping("stepList")
    public ResponseVO getStepList(@RequestParam Integer sceneId) {
        List<StepVO> result = testStepService.getStepList(sceneId);
        return ResponseVO.success(result);
    }

    /**
     * 获取步骤详情
     * 权限：auto:step:view
     */
    @SaCheckPermission("auto:step:view")
    @RequestMapping("stepDetail")
    public ResponseVO getStepDetail(@RequestParam Integer stepId) {
        TestStep result = testStepService.getStepDetail(stepId);
        return ResponseVO.success(result);
    }

    /**
     * 添加步骤
     * 权限：auto:step:create
     */
    @SaCheckPermission("auto:step:create")
    @PostMapping("addStep")
    public ResponseVO add(@RequestBody TestStep step) {
        Boolean result = testStepService.addStep(step);
        return ResponseVO.success(result);
    }

    /**
     * 添加相邻步骤
     * 权限：auto:step:create
     */
    @SaCheckPermission("auto:step:create")
    @PostMapping("addAdjacentStep")
    public ResponseVO addAdjacentStep(@RequestBody AddAdjacentStepDTO adjacentStep) {
        Boolean result = testStepService.addAdjacentStep(adjacentStep);
        return ResponseVO.success(result);
    }

    /**
     * 根据场景id获取关联的步骤
     * 权限：auto:step:view
     */
    @SaCheckPermission("auto:step:view")
    @GetMapping("getStepBySceneId")
    public ResponseVO getStepBySceneId(@RequestParam Integer sceneId) {
        List<TestStep> result = testStepService.getStepBySceneId(sceneId);
        return ResponseVO.success(result);
    }

    /**
     * 更新步骤
     * 权限：auto:step:update
     */
    @SaCheckPermission("auto:step:update")
    @PostMapping("updateStep")
    public ResponseVO updateStep(@RequestBody TestStep step) {
        Boolean result = testStepService.updateStep(step);
        return ResponseVO.success(result);
    }

    /**
     * 删除步骤
     * 权限：auto:step:delete
     */
    @SaCheckPermission("auto:step:delete")
    @GetMapping("deleteStep")
    public ResponseVO deleteStep(@RequestParam Integer stepId) {
        Boolean result = testStepService.deleteStep(stepId);
        return ResponseVO.success(result);
    }

    /**
     * 更新场景步骤排序
     * 权限：auto:step:update
     */
    @SaCheckPermission("auto:step:update")
    @PostMapping("updateStepSort")
    public ResponseVO updateStepSort(@RequestBody List<StepVO> testStepList) {
        Boolean result = testStepService.updateStepSort(testStepList);
        return ResponseVO.success(result);
    }

    /**
     * 复制步骤
     * 权限：auto:step:create
     */
    @SaCheckPermission("auto:step:create")
    @GetMapping("copyStep")
    public ResponseVO copyStep(@RequestParam Integer copyId) {
        Boolean result = testStepService.copyStep(copyId);
        return ResponseVO.success(result);
    }

    /**
     * 禁用/恢复步骤
     * 权限：auto:step:update
     */
    @SaCheckPermission("auto:step:update")
    @GetMapping("disableStep")
    public ResponseVO disableStep(@RequestParam Integer testStepId) {
        Boolean result = testStepService.disableStep(testStepId);
        return ResponseVO.success(result);
    }

    /**
     * 批量启用步骤
     * 权限：auto:step:update
     */
    @SaCheckPermission("auto:step:update")
    @PostMapping("batchEnableStep")
    public ResponseVO enableStep(@RequestBody List<Integer> stepIds) {
        Boolean result = testStepService.batchEnableStep(stepIds);
        return ResponseVO.success(result);
    }

    /**
     * 批量禁用步骤
     * 权限：auto:step:update
     */
    @SaCheckPermission("auto:step:update")
    @PostMapping("batchDisableStep")
    public ResponseVO batchDisableStep(@RequestBody List<Integer> stepIds) {
        Boolean result = testStepService.batchDisableStep(stepIds);
        return ResponseVO.success(result);
    }

    /**
     * 批量删除步骤
     * 权限：auto:step:delete
     */
    @SaCheckPermission("auto:step:delete")
    @PostMapping("batchDeleteStep")
    public ResponseVO batchDeleteStep(@RequestBody List<Integer> stepIds) {
        Boolean result = testStepService.batchDeleteStep(stepIds);
        return ResponseVO.success(result);
    }

    /**
     * 导入已存在的场景步骤
     * 权限：auto:step:create
     */
    @SaCheckPermission("auto:step:create")
    @PostMapping("importExistSceneStep")
    public ResponseVO importExistSceneStep(@RequestBody ImportExistSceneStepDTO existSceneStepDTO) {
        Boolean result = testStepService.importExistSceneStep(existSceneStepDTO);
        return ResponseVO.success(result);
    }
}
