import {TestStep} from "@/types/domain/TestStep";
import {StepExtractor} from "@/types/domain/StepExtractor";
import {TestConditionVO} from "@/types/vo/TestConditionVO";

export interface StepDetailVO {
    step?: TestStep;
    conditions?: TestConditionVO[];
    extractSteps?: StepExtractor[];
}