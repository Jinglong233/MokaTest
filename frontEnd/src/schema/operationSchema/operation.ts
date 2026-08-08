import * as z from "zod/v4";
import {ElementLocatorType} from "@/types/enum/element/ElementLocatorType";
// 操作schema

/**
 * 步骤信息校验
 */
const testStepSchema = z.object({
    // 步骤ID
    id: z.string(),
    // 步骤类型
    type: z.string(),
    // 步骤名称
    stepName: z.string(),
    // 步骤描述
    description: z.string(),
    // 父步骤ID
    parentId: z.string(),
    // 执行顺序
    orderIndex: z.number(),
    // 所属项目ID
    projectId: z.string(),
    // 所属场景ID
    scenarioId: z.string(),
    // 关联元素ID
    elementId: z.string(),
    // 自定义元素类型
    customElementType: z.nativeEnum(ElementLocatorType),
    // 自定义元素值
    customElementValue: z.string(),
});


/**
 * 元素信息校验
 */
const elementSchema = z.object({
    id: z.string(),       // 元素ID
    parentId: z.string(),       // 父id
    elementName: z.string(),       // 元素名称
    elementType: z.string(),       // 类型
    locatorType: z.nativeEnum(ElementLocatorType),       // 定位类型
    locatorValue: z.string(),       // 定位值
    sort: z.number(),       // 排序
    description: z.string(),       // 元素描述
    projectId: z.string(),       // 所属项目ID
    isShared: z.number(),       // 是否共享元素(1-共享，0-私有)
});


const elementDtoSchema = z.object({
    id: z.string().optional(),       // 元素ID
    parentId: z.string().optional(),       // 父id
    elementName: z.string().optional(),       // 元素名称
    elementType: z.string().optional(),       // 类型
    locatorType: z.nativeEnum(ElementLocatorType),       // 定位类型
    locatorValue: z.string(),       // 定位值
    sort: z.number().optional(),       // 排序
    description: z.string().optional(),       // 元素描述
    projectId: z.string().optional(),       // 所属项目ID
    isShared: z.number().optional(),       // 是否共享元素(1-共享，0-私有)
});


export {testStepSchema,elementSchema,elementDtoSchema}