import {any, z} from "zod";
import {ConditionalRelationship} from "@/types/enum/condation/ConditionalRelationship";
import {assertSchema} from "@/schema/operationSchema/assertOperation/AssertSchema";


// while循环
const whileCycleSchema = z.object({
    maxCycleCount: z.number().min(1).max(9999), // 最大循环次数
    conditionRelationship: z.nativeEnum(ConditionalRelationship), // 条件关系
    condations: z.array(assertSchema)
});

export {whileCycleSchema}