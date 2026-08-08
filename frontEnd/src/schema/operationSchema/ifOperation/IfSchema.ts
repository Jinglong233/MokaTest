import {z} from "zod";
import {ConditionalRelationship} from "@/types/enum/condation/ConditionalRelationship";
import {assertSchema} from "@/schema/operationSchema/assertOperation/AssertSchema";

// if操作
export const ifSchema = z.object({
    conditionalRelationship: z.nativeEnum(ConditionalRelationship), // 条件关系
    asserts: z.array(assertSchema) // 断言列表
})

