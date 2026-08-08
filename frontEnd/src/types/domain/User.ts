export class User {
    /**
     * 主键ID
     */
    id?: number;

    /**
     * 用户名，唯一
     */
    username?: string;

    /**
     * 加密后的密码
     */
    password?: string;

    /**
     * 密码盐，用于加密
     */
    salt?: string;

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
     * 创建时间
     */
    createTime?: Date;

    /**
     * 更新时间
     */
    updateTime?: Date;
}