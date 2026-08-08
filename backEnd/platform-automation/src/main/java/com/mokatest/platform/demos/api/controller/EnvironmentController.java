package com.mokatest.platform.demos.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.api.domain.dto.AddEnvDTO;
import com.mokatest.platform.demos.api.domain.requestModel.DataBaseParameter;
import com.mokatest.platform.demos.api.domain.vo.DbTestResult;
import com.mokatest.platform.demos.api.service.ConnectionPoolManager;
import com.mokatest.platform.demos.api.service.EnvironmentService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 环境管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   查看环境：auto:env:view
 *   保存环境：auto:env:create（新增）/ auto:env:update（更新）
 *   删除环境：auto:env:delete
 *   复制环境：auto:env:create
 * admin 角色默认拥有上述所有权限。
 */
@RequestMapping("/env")
@RestController
public class EnvironmentController {

    @Resource
    private EnvironmentService environmentService;

    @Resource
    private ConnectionPoolManager connectionPoolManager;

    /**
     * 保存或更新环境
     * 权限：auto:env:update（覆盖新增与编辑）
     */
    @SaCheckPermission("auto:env:update")
    @RequestMapping("saveOrUpdate")
    public SaResult saveOrUpdate(@RequestBody AddEnvDTO addEnvDTO) {
        return environmentService.saveOrUpdate(addEnvDTO);
    }

    /**
     * 获取指定团队的环境列表
     * 权限：auto:env:view
     */
    @SaCheckPermission("auto:env:view")
    @RequestMapping("getEnvList")
    public SaResult getEnvList(@RequestParam(name = "teamId") Integer teamId) {
        return environmentService.getEnvList(teamId);
    }

    /**
     * 删除环境
     * 权限：auto:env:delete
     */
    @SaCheckPermission("auto:env:delete")
    @RequestMapping("delete")
    public SaResult delete(@RequestParam(name = "id") Integer id) {
        return environmentService.deleteEnv(id);
    }

    /**
     * 复制环境
     * 权限：auto:env:create
     */
    @SaCheckPermission("auto:env:create")
    @RequestMapping("copy")
    public SaResult copy(@RequestParam(name = "id") Integer targetId) {
        return environmentService.copy(targetId);
    }

    /**
     * 测试数据库连接（不持久化，仅验证连通性）
     * 权限：auto:env:update
     */
    @SaCheckPermission("auto:env:update")
    @PostMapping("db/testConnection")
    public SaResult testDbConnection(@RequestBody DataBaseParameter param) {
        if (param == null) {
            return SaResult.error("缺少数据库连接参数");
        }
        DbTestResult result = connectionPoolManager.testConnection(param);
        return SaResult.ok().setData(result);
    }
}
