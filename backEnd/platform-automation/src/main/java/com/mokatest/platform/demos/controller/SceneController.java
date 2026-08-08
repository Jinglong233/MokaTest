package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.domain.ui.dto.scene.SceneBatchExportDTO;
import com.mokatest.platform.demos.domain.ui.dto.scene.SceneBatchImportDTO;
import com.mokatest.platform.demos.domain.ui.vo.ResponseVO;
import com.mokatest.platform.demos.domain.ui.vo.SceneVO;
import com.mokatest.platform.demos.service.SceneService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * UI 自动化场景管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   查看场景/目录：auto:scene:view
 *   创建场景：auto:scene:create
 *   编辑场景：auto:scene:update
 *   删除场景：auto:scene:delete
 *   调试/执行场景：auto:scene:execute
 * admin 角色默认拥有上述所有权限。
 */
@RestController
@RequestMapping("/scene")
public class SceneController {

    @Resource
    private SceneService sceneService;

    /**
     * 获取场景列表（目录列表+场景列表）
     * 权限：auto:scene:view
     */
    @SaCheckPermission("auto:scene:view")
    @RequestMapping("allSceneList")
    public ResponseVO allSceneList(
            @RequestParam(name = "projectId") Integer projectId,
            @RequestParam(name = "sceneCategory", required = false) String sceneCategory) {
        List<SceneVO> sceneVOList = sceneService.allSceneList(projectId, sceneCategory);
        return ResponseVO.success(sceneVOList);
    }

    /**
     * 获取目录列表（只获取目录）
     * 权限：auto:scene:view
     */
    @SaCheckPermission("auto:scene:view")
    @GetMapping("folderList")
    public ResponseVO folderList(
            @RequestParam String projectId,
            @RequestParam(name = "sceneCategory", required = false) String sceneCategory) {
        List<SceneVO> sceneVOList = sceneService.folderList(projectId, sceneCategory);
        return ResponseVO.success(sceneVOList);
    }

    /**
     * 添加场景/目录
     * 权限：auto:scene:create
     */
    @SaCheckPermission("auto:scene:create")
    @PostMapping("addScene")
    public ResponseVO addScene(@RequestBody Scene scene) {
        Boolean result = sceneService.addScene(scene);
        return ResponseVO.success(result);
    }

    /**
     * 导入场景
     * 权限：auto:scene:create
     */
    @SaCheckPermission("auto:scene:create")
    @PostMapping("importScene")
    public ResponseVO importScene(@RequestBody Map<String, Object> sceneData) {
        Boolean result = sceneService.importScene(sceneData);
        return ResponseVO.success(result);
    }

    /**
     * 批量导出场景为 JSON
     * 权限：auto:scene:view
     */
    @SaCheckPermission("auto:scene:view")
    @PostMapping("exportScenes")
    public ResponseVO exportScenes(@RequestBody List<Integer> sceneIds) {
        SceneBatchExportDTO dto = sceneService.exportScenes(sceneIds);
        return ResponseVO.success(dto);
    }

    /**
     * 从 MokaTest 场景 JSON 批量导入场景
     * 权限：auto:scene:create
     */
    @SaCheckPermission("auto:scene:create")
    @PostMapping("importScenesJson")
    public ResponseVO importScenesJson(@RequestBody SceneBatchImportDTO dto) {
        Boolean result = sceneService.importScenesJson(dto);
        return ResponseVO.success(result);
    }

    /**
     * 更新场景/目录
     * 权限：auto:scene:update
     */
    @SaCheckPermission("auto:scene:update")
    @PostMapping("updateScene")
    public ResponseVO updateScene(@RequestBody Scene scene) {
        Boolean result = sceneService.updateScene(scene);
        return ResponseVO.success(result);
    }

    /**
     * 删除场景 or 目录
     * 权限：auto:scene:delete
     */
    @SaCheckPermission("auto:scene:delete")
    @GetMapping("deleteFolderOrScene")
    public ResponseVO deleteFolderOrScene(@RequestParam Integer sceneId) {
        Boolean result = sceneService.deleteFolderOrScene(sceneId);
        return ResponseVO.success(result);
    }

    /**
     * 调试场景
     * 权限：auto:scene:execute
     */
    @SaCheckPermission("auto:scene:execute")
    @PostMapping("debugScene")
    public ResponseVO debugScene(@RequestBody Integer sceneId) {
        Boolean result = sceneService.debugScene(sceneId);
        return ResponseVO.success(result);
    }

    /**
     * 根据id列表获取场景列表
     * 权限：auto:scene:view
     */
    @SaCheckPermission("auto:scene:view")
    @PostMapping("getSceneListByIds")
    public ResponseVO getSceneListByIds(@RequestBody List<Integer> sceneIdList) {
        List<Scene> sceneList = sceneService.getSceneListByIds(sceneIdList);
        return ResponseVO.success(sceneList);
    }

    /**
     * 根据id获取场景信息
     * 权限：auto:scene:view
     */
    @SaCheckPermission("auto:scene:view")
    @GetMapping("getSceneById")
    public ResponseVO getSceneById(@RequestParam Integer sceneId) {
        Scene scene = sceneService.getById(sceneId);
        return ResponseVO.success(scene);
    }

    /**
     * 复制场景
     * 权限：auto:scene:create
     */
    @SaCheckPermission("auto:scene:create")
    @GetMapping("copyScene")
    public ResponseVO copyScene(@RequestParam Integer sceneId) {
        Boolean result = sceneService.copyScene(sceneId);
        return ResponseVO.success(result);
    }

    /**
     * 更新场景排序
     * 权限：auto:scene:update
     */
    @SaCheckPermission("auto:scene:update")
    @PostMapping("updateSceneSort")
    public ResponseVO updateSceneSort(@RequestBody List<SceneVO> sceneList) {
        Boolean result = sceneService.updateSceneSort(sceneList);
        return ResponseVO.success(result);
    }

    /**
     * 更新场景配置
     * 权限：auto:scene:update
     */
    @SaCheckPermission("auto:scene:update")
    @PostMapping("updateSceneSetting")
    public ResponseVO updateSceneSetting(@RequestBody Map<String, Object> sceneSetting) {
        Boolean result = sceneService.updateSceneSetting(sceneSetting);
        return ResponseVO.success(result);
    }
}
