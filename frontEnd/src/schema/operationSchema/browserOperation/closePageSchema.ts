import {z} from "zod";
import {ClosePageMode} from "@/types/enum/browser/ClosePageMode";

// 关闭网页
export const closePageSchema = z.object({
    closepagemode: z.nativeEnum(ClosePageMode), // 关闭页面模式
    customIndex: z.number().optional(), // 自定义关闭页面索引
}).refine((data) => data.closepagemode !== ClosePageMode.CUSTOM_INDEX || data.customIndex !== undefined,
    {
        message: "当 string 为 CUSTOM_INDEX 时，customIndex 必填",
        path: ["customIndex"], // 错误关联到 customIndex 字段
    });