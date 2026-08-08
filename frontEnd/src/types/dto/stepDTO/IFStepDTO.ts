// if判断操作
import {StepBaseDTO} from "@/types/dto/base/StepBaseDTO";
import {ConditionalRelationship} from "@/types/enum/condation/ConditionalRelationship";
import {AssertStepDTO} from "@/types/dto/stepDTO/AssertStepDTO";

export class IFStepDTO extends StepBaseDTO {
    stepType: string = 'IF';

    conditionList: AssertStepDTO[] = [];

    // 条件关系
    conditionalRelationship: ConditionalRelationship | string = "AND";
}






