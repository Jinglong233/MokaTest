import {z} from "zod";
import {elementSchema} from "@/schema/operationSchema/operation";
import {ClickType} from "@/types/enum/click/ClickType";


// 点击操作
export const clickSchema = z.object({
    clickType: z.nativeEnum(ClickType), // 点击类型
    element: elementSchema, // 点击元素
});

