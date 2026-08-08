import {ConditionParameter} from "@/types/domain/ConditionParameter";
import {StepCondition} from "@/types/domain/StepCondition";

export interface TestConditionVO {
    stepCondition?: StepCondition;
    conditionParameters?: ConditionParameter[];
}