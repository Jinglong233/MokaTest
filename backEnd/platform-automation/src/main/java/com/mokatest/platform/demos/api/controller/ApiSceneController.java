package com.mokatest.platform.demos.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mokatest.platform.demos.api.domain.requestModel.DebugApiSceneDTO;
import com.mokatest.platform.demos.api.service.ApiSceneDebugService;
import com.mokatest.platform.demos.domain.ui.vo.ResponseVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API场景控制器
 *
 * 功能：提供纯API场景的调试接口，不依赖 Playwright 浏览器。
 * 注意：场景的增删改查复用 UI 自动化的 SceneController，本 Controller 只提供调试相关接口。
 *
 * 权限说明：调试接口基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   调试API场景：auto:scene:execute
 * admin 角色默认拥有上述所有权限。
 */
@CrossOrigin
@RestController
@RequestMapping("/apiScene")
public class ApiSceneController {

    @Resource
    private ApiSceneDebugService apiSceneDebugService;

    /**
     * 调试API场景
     *
     * @param dto 调试请求DTO
     * @return 场景执行结果
     * 权限：auto:scene:execute
     */
    @SaCheckPermission("auto:scene:execute")
    @PostMapping("debug")
    public ResponseVO debugApiScene(@RequestBody DebugApiSceneDTO dto) {
        ApiSceneDebugService.ApiSceneDebugResult result = apiSceneDebugService.debugScene(dto.getSceneId());
        return ResponseVO.success(result);
    }
}
