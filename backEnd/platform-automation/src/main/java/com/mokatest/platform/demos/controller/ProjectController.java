package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.domain.ui.Project;
import com.mokatest.platform.demos.domain.ui.uiEnum.response.ResponseCode;
import com.mokatest.platform.demos.domain.ui.vo.ResponseVO;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import com.mokatest.platform.demos.service.ProjectService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link SaCheckPermission} 注解声明。
 *   查看项目：project:view
 *   创建项目：project:create
 *   编辑项目：project:update
 *   删除项目：project:delete
 * admin 角色默认拥有上述所有权限。
 */
@RestController
@RequestMapping("/project")
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @Resource
    private ProjectPermissionChecker projectPermissionChecker;

    /**
     * 获取当前登录用户相关的所有项目（创建或负责的项目）
     * 无需 project:view 权限，数据已按当前用户过滤
     */
    @RequestMapping("/allProject")
    public ResponseVO allProject() {
        List<Project> projects = projectService.allProject();
        return ResponseVO.success(projects);
    }

    /**
     * 根据ID获取项目
     * 权限：project:view
     */
    @SaCheckPermission("project:view")
    @RequestMapping("/getProjectById/{id}")
    public SaResult getProjectById(@PathVariable(value = "id") Integer id) {
        return projectService.getProjectById(id);
    }

    /**
     * 获取指定团队的项目列表
     * 权限：团队成员均可查看本团队项目列表（用于工作区/项目选择器只读视图）；非成员且非超管禁止。
     * 进入具体项目仍受项目级权限控制。
     */
    @RequestMapping("/getProjectListByTeamId/{teamId}")
    public SaResult getProjectListByTeamId(@PathVariable(value = "teamId") Integer teamId) {
        String loginId = StpUtil.getLoginIdAsString();
        if (!projectPermissionChecker.isSuperAdmin(loginId)
                && !projectPermissionChecker.isTeamMember(teamId, loginId)) {
            return SaResult.error("无权限查看该团队项目");
        }
        return projectService.getProjectListByTeamId(teamId);
    }

    /**
     * 新建项目
     * 权限：project:create
     */
    @SaCheckPermission("project:create")
    @OperationLog(module = "project", type = OperateType.CREATE, targetType = "project", targetId = "#project.id", targetName = "#project.projectName")
    @PostMapping("/addProject")
    public ResponseVO addProject(@RequestBody Project project) {
        Boolean result = projectService.addProject(project);
        return ResponseVO.success(result);
    }

    /**
     * 更新项目
     * 权限：project:update
     */
    @SaCheckPermission("project:update")
    @OperationLog(module = "project", type = OperateType.UPDATE, targetType = "project", targetId = "#project.id", targetName = "#project.projectName")
    @PostMapping("/updateProject")
    public ResponseVO updateProject(@RequestBody Project project) {
        Boolean result = projectService.updateProject(project);
        return ResponseVO.success(result);
    }

    /**
     * 删除项目（逻辑删除）
     * 权限：project:delete 或项目创建者
     */
    @OperationLog(module = "project", type = OperateType.DELETE, targetType = "project", targetId = "#projectId")
    @PostMapping("/deleteProject/{projectId}")
    public ResponseVO deleteProject(@PathVariable Integer projectId) {
        String loginId = StpUtil.getLoginIdAsString();
        if (!projectService.canDeleteProject(projectId, loginId)) {
            return ResponseVO.failure(ResponseCode.PERMISSION_NO_ACCESS, "无权限删除该项目");
        }
        Boolean result = projectService.deleteProject(projectId);
        return ResponseVO.success(result);
    }

}
