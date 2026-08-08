import {z} from "zod";
import {AssertType} from "@/types/enum/condation/AssertType";
import {elementSchema} from "@/schema/operationSchema/operation";
import {AssertRelationship} from "@/types/enum/condation/AssertRelationship";
import {PageAttribute} from "@/types/enum/page/PageAttribute";

export const assertSchema = z.object({
    assertType: z.nativeEnum(AssertType), // 断言类型
    element: elementSchema.optional(), // 断言元素
    assertText: z.string().optional(), // 断言文本
    elementAttribute: z.string().optional(), // 断言元素属性
    assertRelationship: z.nativeEnum(AssertRelationship).optional(), // 断言关系
    exceptValue: z.string().optional(), // 期望值
    pageAttribute: z.nativeEnum(PageAttribute).optional(), // 断言元素属性
}).superRefine((data, ctx) => {
    if (data.assertType === AssertType.TEXT_EXIST || data.assertType === AssertType.TEXT_NOT_EXIST) {
        if (!data.assertText) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "TEXT 类型必须提供 assertText",
                path: ["assertText"],
            });
        }
    } else if (data.assertType === AssertType.ELEMENT_EXIST || data.assertType === AssertType.ELEMENT_NOT_EXIST) {
        if (!data.element) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "ELEMENT 类型必须提供 element",
                path: ["element"],
            });
        }
    } else if (data.assertType === AssertType.ELEMENT_ARRTRIBUTE) { // 元素属性断言
        if (!data.element) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "ELEMENT 类型必须提供 element",
                path: ["element"],
            });
        }
        if (!data.elementAttribute) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "必须提供元素属性",
                path: ["elementAttribute"],
            });
        }
        if (!data.assertRelationship) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "必须提供断言关系",
                path: ["assertRelationship"],
            });
        }
        if (!data.exceptValue) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "必须提供期望值",
                path: ["exceptValue"],
            });
        }
    } else if (data.assertType === AssertType.PAGE_ARRTRIBUTE) { // 页面属性断言
        if (!data.pageAttribute) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "必须提供页面属性",
                path: ["pageAttribute"],
            });
        }
        if (!data.assertRelationship) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "必须提供断言关系",
                path: ["assertRelationship"],
            });
        }
        if (!data.exceptValue) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "必须提供期望值",
                path: ["exceptValue"],
            });
        }

    }
});
