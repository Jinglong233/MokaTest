import {z} from "zod";
import {ExtractType} from "@/types/enum/extract/ExtractType";
import {elementSchema} from "@/schema/operationSchema/operation";
import {ElementExtractValueType} from "@/types/enum/extract/ElementExtractValueType";
import {PageExtractValueType} from "@/types/enum/extract/PageExtractValueType";


// 提取操作
const extractSchema = z.object({
    variableName: z.string(), // 变量名
    extractType: z.nativeEnum(ExtractType), // 提取类型
    element: elementSchema.optional(), // 提取的目标元素
    elementExtractType: z.nativeEnum(ElementExtractValueType).optional(), // 元素提取类型
    elementAttribute: z.string().optional(),
    pageExtractType: z.nativeEnum(PageExtractValueType).optional(), // 页面提取类型
}).superRefine((data, ctx) => {
    if (data.extractType === ExtractType.ELEMENT) { // 提取元素相关的
        if (!data.element) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: '请选择提取的目标元素',
                path: ['element']
            });
        }
        if (!data.elementExtractType) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: '请选择元素提取类型',
                path: ['elementExtractType']

            });
        }
        if (data.elementExtractType === ElementExtractValueType.ATTRIBUTE) {
            if (!data.elementAttribute) {
                ctx.addIssue({
                    code: z.ZodIssueCode.custom,
                    message: '请选择元素属性',
                    path: ['elementAttribute']
                });
            }
        }
    }
    if (data.extractType === ExtractType.PAGE) { // 提取页面相关
        if (!data.pageExtractType) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: '请选择页面提取类型',
                path: ['pageExtractType']
            });
        }
    }

})
export {extractSchema}