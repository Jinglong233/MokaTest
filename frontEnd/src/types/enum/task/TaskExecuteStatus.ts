export enum TaskExecuteStatus {
    // 未开始
    NOT_STARTED = "NOT_STARTED",
    // 进行中
    IN_PROGRESS = "IN_PROGRESS",

}


/**
 * 根据TaskExecuteStatus返回对应的中文描述
 * @param taskExecuteStatus 任务执行状态（字符串形式）
 * @returns 对应的中文描述
 */
export function getTaskExecuteStatusDescription(taskExecuteStatus: string): string {
    switch (taskExecuteStatus) {
        case TaskExecuteStatus.NOT_STARTED:
            return '未开始';
        case TaskExecuteStatus.IN_PROGRESS:
            return '进行中';
        default:
            return '未知状态';
    }
}





