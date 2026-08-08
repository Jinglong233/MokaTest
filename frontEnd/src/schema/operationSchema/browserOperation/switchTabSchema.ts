// 切换tab页签
import {z} from "zod";
import {SwitchTabMode} from "@/types/enum/browser/SwitchTabMode";

// 切换页签
export const switchTabSchema = z.object({
    switchTabMode: z.nativeEnum(SwitchTabMode), // 切换页面模式
    customIndex: z.number().optional(), // 切换tab索引
}).refine((data) => data.switchTabMode !== SwitchTabMode.CUSTOM_INDEX || data.customIndex !== undefined,
    {
        message: "当 switchTabMode 为 CUSTOM_INDEX 时，customIndex 必填",
        path: ["customIndex"], // 错误关联到 customIndex 字段
    });