// while循环操作
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {ConditionalRelationship} from "@/types/enum/condation/ConditionalRelationship";
import {AssertStepDTO} from "@/types/dto/stepDTO/AssertStepDTO";

export class WhileStepDTO extends StepBaseDTO {
    stepType: string = 'WHILE';

    // 条件关系
    conditionalRelationship: ConditionalRelationship | string = "AND";

    // 条件列表
    conditionList: AssertStepDTO[] = [];

    // 最大循环次数
    maxLoopCount: number = 0;
}






