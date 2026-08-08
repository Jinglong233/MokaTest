import {z} from "zod";
import {elementSchema} from "@/schema/operationSchema/operation";

// 键盘输入
export const keyboardInputSchema = z.object({
    // todo 指定按键待做
    // keyboardValue: z.string(), // 键盘输入

    inputValue: z.string(), // 输入内容
    // todo 当键盘输入的时候，才选择元素。如果是空格键这些，不需要元素
    element: elementSchema.optional(), // 元素
    isAdditional: z.number(), // 是否追加
});
