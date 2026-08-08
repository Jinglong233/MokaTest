package com.mokatest.platform.demos.controller;


import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.domain.ui.Team;
import com.mokatest.platform.demos.domain.ui.dto.addDTO.AddTeamMemberDTO;
import com.mokatest.platform.demos.domain.ui.dto.addDTO.TeamMemberOperateDTO;
import com.mokatest.platform.demos.domain.ui.dto.addDTO.UpdateTeamMemberRoleDTO;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import com.mokatest.platform.demos.service.TeamService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 团队管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   团队成员管理（增删改查、状态变更）：team:member:manage
 *   团队成员角色分配：team:role:manage
 * 前端需在 Header 中传 X-Team-Id，供 SaToken 解析当前团队上下文。
 */
@RequestMapping("/team/")
@RestController
public class TeamController {

    @Resource
    private TeamService teamService;

    @Resource
    private ProjectPermissionChecker projectPermissionChecker;

    /**
     * 获取登录人所在团队列表
     */
    @RequestMapping("getTeamList")
    public SaResult getTeamList() {
        return teamService.getTeamList();
    }

    /**
     * 创建团队
     */
    @RequestMapping("createTeam")
    @OperationLog(module = "team", type = OperateType.CREATE, targetType = "team", targetId = "#team.id", targetName = "#team.teamName")
    public SaResult createTeam(@RequestBody Team team) {
        return teamService.createTeam(team);
    }

    /**
     * 修改团队信息
     * 权限：team:member:manage
     */
    @SaCheckPermission("team:member:manage")
    @OperationLog(module = "team", type = OperateType.UPDATE, targetType = "team", targetId = "#team.id", targetName = "#team.teamName")
    @RequestMapping("updateTeam")
    public SaResult updateTeam(@RequestBody Team team) {
        return teamService.updateTeam(team);
    }

    /**
     * 团队添加成员
     * 权限：team:member:manage
     */
    @SaCheckPermission("team:member:manage")
    @OperationLog(module = "team", type = OperateType.CREATE, targetType = "teamMember",
            description = "'将 ' + @logText.userNames(#addTeamMemberDTO.userList) + ' 加入团队「' + @logText.teamName(#addTeamMemberDTO.teamId) + '」'")
    @RequestMapping("addTeamMember")
    public SaResult addTeamMember(@RequestBody AddTeamMemberDTO addTeamMemberDTO) {
        return teamService.addTeamMember(addTeamMemberDTO);
    }

    /**
     * 获取团队成员列表
     * 权限：团队成员均可查看（用于项目列表/团队成员只读视图）；非成员且非超管禁止
     */
    @RequestMapping("members/{teamId}")
    public SaResult getTeamMembers(@PathVariable Integer teamId) {
        String loginId = StpUtil.getLoginIdAsString();
        if (!projectPermissionChecker.isSuperAdmin(loginId)
                && !projectPermissionChecker.isTeamMember(teamId, loginId)) {
            return SaResult.error("无权限查看该团队成员");
        }
        return teamService.getTeamMembers(teamId);
    }

    /**
     * 修改成员角色
     * 权限：team:role:manage
     */
    @SaCheckPermission("team:role:manage")
    @OperationLog(module = "team", type = OperateType.UPDATE, targetType = "teamMember",
            targetId = "#dto.userId",
            targetName = "@logText.userName(#dto.userId)",
            description = "'在团队「' + @logText.teamName(#dto.teamId) + '」中的角色改为 ' + @logText.roleName(#dto.roleId)")
    @RequestMapping("member/updateRole")
    public SaResult updateMemberRole(@RequestBody UpdateTeamMemberRoleDTO dto) {
        return teamService.updateMemberRole(dto);
    }

    /**
     * 移除团队成员
     * 权限：team:member:manage
     */
    @SaCheckPermission("team:member:manage")
    @OperationLog(module = "team", type = OperateType.DELETE, targetType = "teamMember",
            targetId = "#dto.userId",
            targetName = "@logText.userName(#dto.userId)",
            description = "'从团队「' + @logText.teamName(#dto.teamId) + '」移除成员'")
    @RequestMapping("member/remove")
    public SaResult removeMember(@RequestBody TeamMemberOperateDTO dto) {
        return teamService.removeMember(dto);
    }

    /**
     * 启用/禁用团队成员
     * 权限：team:member:manage
     */
    @SaCheckPermission("team:member:manage")
    @OperationLog(module = "team", type = OperateType.UPDATE, targetType = "teamMember",
            targetId = "#dto.userId",
            targetName = "@logText.userName(#dto.userId)",
            description = "'在团队「' + @logText.teamName(#dto.teamId) + '」中' + (#dto.status == 1 ? '启用' : '禁用')")
    @RequestMapping("member/updateStatus")
    public SaResult updateMemberStatus(@RequestBody TeamMemberOperateDTO dto) {
        return teamService.updateMemberStatus(dto);
    }

    /**
     * 解散团队（逻辑删除）
     * 权限：team:delete 或团队创建者
     */
    @OperationLog(module = "team", type = OperateType.DELETE, targetType = "team", targetId = "#teamId")
    @RequestMapping("deleteTeam/{teamId}")
    public SaResult deleteTeam(@PathVariable Integer teamId) {
        String loginId = StpUtil.getLoginIdAsString();
        if (!teamService.canDeleteTeam(teamId, loginId)) {
            return SaResult.error("无权限解散该团队");
        }
        return teamService.deleteTeam(teamId);
    }
}
