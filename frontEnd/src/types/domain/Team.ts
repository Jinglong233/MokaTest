export class Team {
    id?: number;

    /**
     * 团队名称
     */
    teamName?: string;

    /**
     * 团队人数
     */
    teamNumber: number = 0;

    /**
     * 状态：0-禁用，1-正常
     */
    status: number = 1;

    /**
     * 是否个人团队：0-否，1-是
     */
    isPersonal?: number;

    /**
     * 描述
     */
    description?: string;

    /**
     * 创建时间
     */
    createdAt?: Date;

    /**
     * 更新时间
     */
    updatedAt?: Date;

    /**
     * 创建人ID
     */
    createUserId?: string;

    /**
     * 更新人id
     */
    updateUserId?: string;

    /**
     * 团队管理员ID（team.owner_id，团队管理员唯一来源）
     */
    ownerId?: number;

    /**
     * 是否已删除：0-未删除，1-已删除
     */
    isDeleted?: number;

    /**
     * 删除时间
     */
    deletedAt?: Date;

    /**
     * 当前登录用户在该团队是否为管理员（后端返回，仅用于团队切换下拉过滤）
     */
    manageable?: boolean;

    /**
     * 创建人显示名（后端返回，列表展示用）
     */
    createUserName?: string;

}