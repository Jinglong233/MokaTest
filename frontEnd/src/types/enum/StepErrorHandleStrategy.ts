// 步骤错误处理策略
export enum StepErrorHandleStrategy {
    // 忽略
    IGNORE = "IGNORE",
    // 重试
    RETRY = "RETRY",
    // 停止
    STOP = "STOP"

}