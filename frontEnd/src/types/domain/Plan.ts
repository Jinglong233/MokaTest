import {SceneExecuteType} from "@/types/enum/task/SceneExecuteType";
import {TaskType} from "@/types/enum/task/TaskType";

export class Plan {
    /**
     * id
     */
    id?: number;

    /**
     * 关联计划配置信息
     */
    planRunningSetting?: string;

    /**
     * 计划名称
     */
    planName?: string;

    /**
     * cron表达式
     */
    cronExpression: string = '0 * * * * *';

    /**
     * 计划描述
     */
    description?: string;

    /**
     * 场景执行类型（顺序、并发）
     */
    executionType: Object = SceneExecuteType.ORDER;

    /**
     * 场景状态提取
     */
    sceneStatusExtract?: number;

    /**
     * 参数
     */
    params?: Object;

    /**
     * 任务类型
     */
    taskType: Object = TaskType.NORMAL;

    /**
     * 计划分类：UI / API / MIXED
     */
    planCategory?: string;

    /**
     * 状态
     */
    status?: Object;

    /**
     * 执行后是否发送 Webhook 通知
     */
    webhookEnabled?: number;

    /**
     * 关联的 Webhook 配置ID，逗号分隔（如 "1,2"）
     */
    webhookIds?: string;

    /**
     * 创建人id
     */
    createUserId?: number;

    /**
     * 更新人id
     */
    updateUserId?: number;

    /**
     * 创建时间
     */
    createdAt?: Date;

    /**
     * 更新时间
     */
    updatedAt?: Date;

}