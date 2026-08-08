export class UserTeamVO {
  /**
   * 团队ID
   */
  teamId?: number;

  /**
   * 团队名称
   */
  teamName?: string;

  /**
   * 角色ID
   */
  roleId?: number;

  /**
   * 角色名称
   */
  roleName?: string;

  /**
   * 角色编码
   */
  roleCode?: string;

  /**
   * 成员状态：0-禁用，1-正常
   */
  status?: number;
}
