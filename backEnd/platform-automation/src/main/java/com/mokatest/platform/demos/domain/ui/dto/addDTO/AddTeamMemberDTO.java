package com.mokatest.platform.demos.domain.ui.dto.addDTO;

import lombok.Data;

import java.util.List;

@Data
public class AddTeamMemberDTO {
    private String teamId;
    private List<String> userList;  // 直接用 List，不用自己解析

    /** 邀请入团队的同时，加入指定项目并分配项目角色（可选） */
    private Boolean assignProjectRole;
    /** 要加入的项目（assignProjectRole=true 时必填，且必须属于当前团队） */
    private Integer projectId;
    /** 项目角色模板ID；为空时默认分配内置只读模板「项目成员」（project_member） */
    private Long projectRoleId;
    /** 指定为项目管理员（变更 project.owner_id，旧管理员自动降级为项目成员）；为 true 时忽略 projectRoleId */
    private Boolean assignAsProjectAdmin;
    /** 角色有效期（yyyy-MM-dd HH:mm:ss），为空表示永久 */
    private String expireTime;
}
