export class Role {
    id?: number;
    name?: string;
    code?: string;
    /**
     * 范围类型：SYSTEM-内置角色（team_admin/team_member/project_admin），TEMPLATE-自定义模板
     */
    scopeType?: string;
    /**
     * TEMPLATE 范围：null=全局模板，project.id=项目模板
     */
    scopeId?: number;
    teamId?: number;
    description?: string;
    isSystem?: number;
    createTime?: string;
    updateTime?: string;
}
