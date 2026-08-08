import {z} from "zod";

// 步骤设置
export const settingSchema = z.object({
    isSetting: z.number(), // 是否开启
    preExecuteWaitingTime: z.number().min(0).max(9999), // 预执行等待时间
    waitingTimeAfterExecution: z.number().min(0).max(9999), // 执行后等待时间
    timeout: z.number().min(0).max(9999), // 超时时间
});

