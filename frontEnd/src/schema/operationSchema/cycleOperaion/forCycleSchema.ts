// for循环
import {z} from "zod";

export const forCycleSchema = z.object({
    times: z.number().min(0).max(9999), // 循环次数
    // todo 循环文件待做
});