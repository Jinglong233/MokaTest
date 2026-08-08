import {z} from "zod";

// 等待固定时长
export const waitFixedDurationSchema = z.object({
    waitTime: z.number().min(0).max(9999), // 等待时间
});
