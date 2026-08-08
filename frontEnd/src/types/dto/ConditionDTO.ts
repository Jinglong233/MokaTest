// 条件传输对象
import {AssertStepDTO} from "@/types/dto/stepDTO/AssertStepDTO";
import {ConditionalRelationship} from "@/types/enum/condation/ConditionalRelationship";

export class ConditionDTO {
    conditionalRelationship: ConditionalRelationship | string = "AND";// 条件关系
    asserts: AssertStepDTO[] = []; // 断言列表
}