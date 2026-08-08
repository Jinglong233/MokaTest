import {z} from "zod";
import {elementSchema} from "@/schema/operationSchema/operation";

// 打开网页
export const openPageSchema = z.object({
    url: z.string(), // 网址
    recover: z.number(), // 是否覆盖输入
});