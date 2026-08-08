import {BasePageQueryDTO} from "@/types/dto/queryDTO/BasePageQueryDTO";

export class ReportQueryDTO extends BasePageQueryDTO {


    // 项目id
    projectId: string = '';

    /**
     * 执行者id
     */
    executionUserId: string = '';

    /**
     * 执行者姓名
     */
    executionUserName: string = '';

    /**
     * 任务类型
     */
    taskType: string = '';


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
    planName: string = '';

    /**
     * 报告名称
     */
    reportName: string = '';

    /**
     * 场景列表
     */
    scenes: Object = '';


}
