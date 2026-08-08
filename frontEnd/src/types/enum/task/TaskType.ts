export enum TaskType {
    // 普通任务
    NORMAL = "NORMAL",
    // 定时任务
    TIMING = "TIMING"
}


/**
 * 根据TaskType返回对应的中文描述
 * @param taskType 任务类型
 * @returns 对应的中文描述
 */
export function getTaskTypeDescription(taskType: TaskType): string {
    switch (taskType) {
        case TaskType.NORMAL:
            return '普通任务';
        case TaskType.TIMING:
            return '定时任务';
        default:
            return '未知任务类型';
    }
}