export class Report {
    /**
     * 报告id
     */
    id?: number;

    /**
     * 场景数量
     */
    sceneNumber?: number;

    /**
     * 步骤总数
     */
    stepNumber?: number;

    /**
     * 断言总数
     */
    assertNumber?: number;

    /**
     * 执行时长
     */
    executionDuration?: number;

    /**
     * 执行者id
     */
    executionUserId?: string;

    /**
     * 执行者姓名
     */
    executionUserName?: string;

    /**
     * 任务类型
     */
    taskType?: string;

    /**
     * 报告分类：UI / API / MIXED
     */
    reportCategory?: string;

    /**
     * 场景执行错误数量
     */
    sceneErrorNumber?: number;

    /**
     * 场景执行成功数量
     */
    sceneSuccessNumber?: number;

    /**
     * 断言成功数量
     */
    assertSuccessNumber?: number;

    /**
     * 断言失败数量
     */
    assertErrorNumber?: number;

    /**
     * 断言跳过数量
     */
    assertSkipNumber?: number;

    /**
     * 创建时间
     */

    createTime?: Date;

    /**
     * 结束时间
     */

    endTime?: Date;

    /**
     * 状态
     */
    status?: number;

    /**
     * 关联计划id
     */
    planId?: number;

    /**
     * 计划名称
     */
    planName?: string;

    /**
     * 报告名称
     */

    reportName?: string;

    /**
     * 视频执行地址
     */
    videoPath?: Object;

    /**
     * 场景列表
     */
    scenes?: Object;

    /**
     * 步骤成功数量
     */
    stepSuccessNumber?: number;

    /**
     * 步骤失败数量
     */
    stepErrorNumber?: number;

    /**
     * 步骤跳过数量
     */
    stepSkipNumber?: number;

    /**
     * 是否已删除：0-未删除，1-已删除
     */
    isDeleted?: number = 0;

    /**
     * 删除时间
     */
    deletedAt?: Date;

}