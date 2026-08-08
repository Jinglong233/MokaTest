export class AddTeamMemberDTO {
    teamId?: string | number;
    userList?: string[];
    /** 邀请入团队的同时，加入指定项目并分配项目角色（可选） */
    assignProjectRole?: boolean;
    /** 要加入的项目（assignProjectRole=true 时必填，仅当前团队项目） */
    projectId?: number;
    /** 项目角色模板ID；为空时默认分配内置「项目成员」 */
    projectRoleId?: number;
    /** 指定为项目管理员（替换现任管理员，旧管理员降级为项目成员） */
    assignAsProjectAdmin?: boolean;
    /** 角色有效期（YYYY-MM-DD HH:mm:ss），为空表示永久 */
    expireTime?: string;
}
