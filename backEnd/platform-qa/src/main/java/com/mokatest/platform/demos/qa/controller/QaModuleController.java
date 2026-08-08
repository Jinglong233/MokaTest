package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.domain.QaModule;
import com.mokatest.platform.demos.qa.service.QaModuleService;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * 质量管理 - 所属模块管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link SaCheckPermission} 注解声明。
 *   查看模块：qa:module:view
 *   创建模块：qa:module:create
 *   编辑模块：qa:module:update
 *   删除模块：qa:module:delete
 * admin 角色默认拥有上述所有权限。
 */
@Slf4j
@RestController
@RequestMapping("/qa/module")
@RequiredArgsConstructor
public class QaModuleController {

    private final QaModuleService qaModuleService;

    /**
     * 模块列表
     * 权限：qa:module:view
     */
    @SaCheckPermission("qa:module:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer projectId) {
        return qaModuleService.listByProject(projectId);
    }

    /**
     * 模块树
     * 权限：qa:module:view
     */
    @SaCheckPermission("qa:module:view")
    @GetMapping("/tree")
    public SaResult tree(@RequestParam Integer projectId) {
        return qaModuleService.tree(projectId);
    }

    /**
     * 保存模块（创建/更新统一入口）
     * 权限：创建用 qa:module:create，更新用 qa:module:update
     */
    @SaCheckPermission("qa:module:create")
    @OperationLog(module = "qa", type = OperateType.CREATE, targetType = "qaModule", targetId = "#module.id", targetName = "#module.moduleName")
    @PostMapping("/save")
    public SaResult save(@RequestBody QaModule module) {
        if (module.getProjectId() == null) {
            return SaResult.error("缺少项目ID");
        }
        if (module.getId() == null) {
            module.setCreateTime(new Date());
            module.setUpdateTime(new Date());
        } else {
            module.setUpdateTime(new Date());
        }
        return qaModuleService.saveOrUpdateModule(module);
    }

    /**
     * 更新模块
     * 权限：qa:module:update
     */
    @SaCheckPermission("qa:module:update")
    @OperationLog(module = "qa", type = OperateType.UPDATE, targetType = "qaModule", targetId = "#module.id", targetName = "#module.moduleName", compareClass = QaModule.class)
    @PostMapping("/update")
    public SaResult update(@RequestBody QaModule module) {
        if (module.getId() == null) {
            return SaResult.error("缺少模块ID");
        }
        QaModule existing = qaModuleService.getById(module.getId());
        if (existing == null) {
            return SaResult.error("模块不存在");
        }
        module.setUpdateTime(new Date());
        return qaModuleService.saveOrUpdateModule(module);
    }

    /**
     * 删除模块
     * 权限：qa:module:delete
     */
    @SaCheckPermission("qa:module:delete")
    @OperationLog(module = "qa", type = OperateType.DELETE, targetType = "qaModule", targetId = "#id", compareClass = QaModule.class)
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return qaModuleService.deleteModule(id);
    }

    /**
     * 拖拽排序模块
     * 权限：qa:module:update
     */
    @SaCheckPermission("qa:module:update")
    @OperationLog(module = "qa", type = OperateType.UPDATE, targetType = "qaModule", targetId = "#moduleId")
    @PostMapping("/sort")
    public SaResult sort(@RequestParam Integer moduleId,
                         @RequestParam Integer targetParentId,
                         @RequestParam Integer targetIndex) {
        return qaModuleService.sortModule(moduleId, targetParentId, targetIndex);
    }
}
