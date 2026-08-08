export class Project {

    id?: number;
    /**
     * 项目名称
     */
    projectName: string = '';

    /**
     * 描述
     */
    description: string = '';

    /**
     * 团队id
     */
    teamId: string = '';

    /**
     * 更新人ID
     */
    updateUserId: string = '';

    /**
     * 覆盖率
     */
    coverage: number = 0;

    /**
     * 状态
     */
    status?: Object;

    /**
     * api测试用例数量
     */
    apiTotal: number = 0;

    /**
     * UI测试场景数量
     */
    uiTotal: number = 0;

    /**
     * 性能测试用例数量
     */
    performanceTotal: number = 0;

    /**
     * 自动化任务数量
     */
    planTotal: number = 0;

    /**
     * UI测试报告通过率
     */
    uiPass: number = 0;

    /**
     * 标签
     */
    tagClassify: Object = [];

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
    createUserId: string = '';

    /**
     * 创建人姓名
     */
    createUserName: string = '';

    /**
     * 项目管理员ID（创建项目时指定，即 project.owner_id）
     */
    ownerId?: number;

    /**
     * 当前登录用户在本项目的角色名称（项目列表展示用，后端填充）
     */
    myRoleName?: string;

    /**
     * 当前登录用户在本项目的角色编码（项目列表展示用，后端填充）
     */
    myRoleCode?: string;

    /**
     * 是否已删除：0-未删除，1-已删除
     */
    isDeleted?: number;

    /**
     * 删除时间
     */
    deletedAt?: Date;

}