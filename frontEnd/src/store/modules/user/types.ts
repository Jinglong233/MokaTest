export type RoleType = '' | '*' | 'admin' | 'user' | 'super_admin';

export class UserState {
    /**
     * 主键ID
     */
    id?: string = '';

    /**
     * 用户名，唯一
     */
    username?: string = '';

    /**
     * 昵称
     */
    nickname?: string = '';

    /**
     * 头像URL
     */
    avatar?: string = '';

    /**
     * 手机号
     */
    phone?: string = '';

    /**
     * 邮箱
     */
    email?: string = '';

    /**
     * 状态：0-禁用，1-正常
     */
    status?: string = '';
    role?: RoleType;
}
