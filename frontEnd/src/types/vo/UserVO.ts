import { UserTeamVO } from './UserTeamVO';

export class UserVO {
  /**
   * 主键ID
   */
  id?: number;

  username?: string;

  /**
   * 昵称
   */
  nickname?: string;

  /**
   * 头像URL
   */
  avatar?: string;

  /**
   * 手机号
   */
  phone?: string;

  /**
   * 邮箱
   */
  email?: string;

  /**
   * 状态：0-禁用，1-正常
   */
  status?: number;

  /**
   * 全局角色：super_admin-超级管理员，user-普通用户
   */
  role?: string;

  /**
   * 所属团队及角色列表（超管查看全部用户时填充）
   */
  teams?: UserTeamVO[];

  /**
   * 创建时间
   */
  createTime?: Date;

  /**
   * 更新时间
   */
  updateTime?: Date;
}
