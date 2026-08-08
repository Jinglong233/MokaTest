package com.mokatest.platform.demos.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.api.domain.dto.AddGlobalVarDTO;
import com.mokatest.platform.demos.api.service.GlobalVarService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局变量管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   查看全局变量：auto:globalvar:view
 *   保存全局变量：auto:globalvar:update（覆盖新增与编辑）
 *   删除全局变量：auto:globalvar:delete
 * admin 角色默认拥有上述所有权限。
 */
@RestController
@RequestMapping("/globalVar")
public class GlobalEnvController {

    @Resource
    private GlobalVarService globalVarService;

    /**
     * 保存或更新全局变量
     * 权限：auto:globalvar:update
     */
    @SaCheckPermission("auto:globalvar:update")
    @RequestMapping("saveOrUpdate")
    public SaResult saveOrUpdate(@RequestBody AddGlobalVarDTO addGlobalVarDTO) {
        return globalVarService.saveOrUpdate(addGlobalVarDTO);
    }

    /**
     * 获取指定团队的全局参数信息
     * 权限：auto:globalvar:view
     */
    @SaCheckPermission("auto:globalvar:view")
    @RequestMapping("getGlobalArgList")
    public SaResult getGlobalArgList(@RequestParam(name = "teamId") Integer teamId) {
        return globalVarService.getGlobalArgList(teamId);
    }

    /**
     * 删除指定id的全局参数
     * 权限：auto:globalvar:delete
     */
    @SaCheckPermission("auto:globalvar:delete")
    @RequestMapping("deleteById")
    public SaResult deleteById(@RequestParam(name = "id") Integer id) {
        return globalVarService.deleteGlobalVar(id);
    }
}
